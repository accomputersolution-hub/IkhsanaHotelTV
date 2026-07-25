import { db } from './firebase-config.js';
import {
  collection,
  doc,
  setDoc,
  onSnapshot,
  serverTimestamp,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import {
  escapeHtml,
  toast,
  showConnectionError,
  hideConnectionError,
  openModal,
  closeModal,
  setupModalClose,
} from './utils.js';
import { normalizeRoom, paths, logFirestoreWrite, logFirestoreListen } from './paths.js';
import { flushRoomSession, newSessionKey } from './session-reset.js';
import { getHotelId, onHotelChange } from './tenant-context.js';

const ROOM_STATUSES = {
  vacant: { label: 'Vacant', badge: 'room-status-vacant' },
  occupied: { label: 'Occupied', badge: 'room-status-occupied' },
  housekeeping: { label: 'Housekeeping', badge: 'room-status-housekeeping' },
  maintenance: { label: 'Maintenance', badge: 'room-status-housekeeping' },
  needs_cleaning: { label: 'Needs Cleaning', badge: 'room-status-housekeeping' },
};

const DEFAULT_ROOMS = Array.from({ length: 16 }, (_, i) => {
  const num = 101 + i;
  return {
    roomNumber: String(num),
    roomType: num <= 108 ? 'deluxe' : 'suite',
  };
});

let roomsCache = [];
let seedAttempted = false;
let roomsUnsub = null;

export function initGuests() {
  setupCheckInModal();
  setupAddRoomModal();
  onHotelChange(() => {
    seedAttempted = false;
    listenRooms();
  });
}

function listenRooms() {
  if (roomsUnsub) {
    roomsUnsub();
    roomsUnsub = null;
  }
  const hotelId = getHotelId();
  if (!hotelId) {
    roomsCache = [];
    renderRoomGrid([]);
    updateSummaryCounters([]);
    return;
  }

  const collectionPath = `Hotels/${hotelId}/Rooms`;
  logFirestoreListen('Guest Rooms', collectionPath);

  roomsUnsub = onSnapshot(
    collection(db, 'Hotels', hotelId, 'Rooms'),
    async (snapshot) => {
      hideConnectionError();

      if (!snapshot.docs.length && !seedAttempted) {
        seedAttempted = true;
        await seedDefaultRooms();
        return;
      }

      roomsCache = snapshot.docs
        .map((d) => ({ id: d.id, ...d.data() }))
        .sort((a, b) => String(a.id).localeCompare(String(b.id), undefined, { numeric: true }));

      updateSummaryCounters(roomsCache);
      renderRoomGrid(roomsCache);
    },
    (err) => {
      console.error('[Firestore ERROR] Rooms listener:', err);
      showConnectionError('Could not load guest rooms from Firestore.');
    },
  );
}

async function seedDefaultRooms() {
  try {
    await Promise.all(
      DEFAULT_ROOMS.map(({ roomNumber, roomType }) => {
        const payload = {
          roomNumber,
          roomType,
          status: 'vacant',
          guestName: 'Guest',
          guestPhone: '',
          checkOutDate: '',
          hotelName: 'Ikhsana Hotel',
          updatedAt: serverTimestamp(),
        };
        return setDoc(doc(db, 'Hotels', getHotelId(), 'Rooms', roomNumber), payload, { merge: true });
      }),
    );
    console.log('[PMS] Seeded default rooms 101–116');
    toast('Initialized rooms 101–116');
  } catch (err) {
    console.error('[Firestore ERROR] Room seed failed:', err);
    toast('Failed to initialize default rooms', 'error');
  }
}

function deriveStatus(room) {
  if (room.status && ROOM_STATUSES[room.status]) return room.status;
  const name = room.guestName?.trim();
  if (name && name !== 'Guest') return 'occupied';
  return 'vacant';
}

/** Shared with Housekeeping Queue — mark room vacant & cleaned after turnover */
export async function markRoomCleanAndReady(roomNumber) {
  await writeRoom(roomNumber, {
    status: 'vacant',
    guestName: 'Guest',
    guestPhone: '',
    checkOutDate: '',
    cleaned: true,
    cleanedAt: serverTimestamp(),
    roomNumber,
  });
}

function updateSummaryCounters(rooms) {
  const counts = { total: rooms.length, occupied: 0, vacant: 0, housekeeping: 0 };

  rooms.forEach((room) => {
    const status = deriveStatus(room);
    if (status === 'occupied') counts.occupied += 1;
    else if (status === 'vacant') counts.vacant += 1;
    else if (status === 'housekeeping' || status === 'maintenance') counts.housekeeping += 1;
  });

  setText('pms-stat-total', counts.total);
  setText('pms-stat-occupied', counts.occupied);
  setText('pms-stat-vacant', counts.vacant);
  setText('pms-stat-housekeeping', counts.housekeeping);
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

function formatRoomType(type) {
  if (type === 'suite') return 'Suite';
  return 'Deluxe Room';
}

function formatCheckOut(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr + 'T12:00:00');
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

function guestSubtitle(room, status) {
  if (status === 'occupied') {
    const parts = [room.guestName || 'Guest'];
    if (room.guestPhone) parts.push(room.guestPhone);
    if (room.checkOutDate) parts.push(`Out: ${formatCheckOut(room.checkOutDate)}`);
    return parts.join(' · ');
  }
  if (status === 'housekeeping' || status === 'maintenance') {
    return 'Awaiting housekeeping — not ready for guest';
  }
  return 'Cleaned & Ready for Guest';
}

function renderRoomGrid(rooms) {
  const container = document.getElementById('guest-room-list');
  if (!container) return;

  if (!rooms.length) {
    container.innerHTML = `
      <p class="empty-state col-span-full">
        No rooms configured yet. Click <strong>+ Add Room</strong> to create one.
      </p>`;
    return;
  }

  container.innerHTML = rooms
    .map((room) => {
      const roomNum = String(room.id);
      const status = deriveStatus(room);
      const meta = ROOM_STATUSES[status] || ROOM_STATUSES.vacant;
      const roomType = formatRoomType(room.roomType);
      const subtitle = guestSubtitle(room, status);

      let actions = '';
      if (status === 'vacant') {
        actions = `<button data-action="check-in" data-room="${escapeHtml(roomNum)}" class="room-action-btn room-action-primary">Check-In</button>`;
      } else if (status === 'occupied') {
        actions = `
          <button data-action="check-out" data-room="${escapeHtml(roomNum)}" class="room-action-btn room-action-danger">Check-Out</button>
          <button data-action="edit-guest" data-room="${escapeHtml(roomNum)}" class="room-action-btn">Edit Guest</button>`;
      } else {
        actions = `
          <button data-action="mark-ready" data-room="${escapeHtml(roomNum)}" class="room-action-btn room-action-primary">Mark Ready</button>
          <button data-action="mark-maintenance" data-room="${escapeHtml(roomNum)}" class="room-action-btn">Maintenance</button>`;
      }

      return `
      <div class="room-card-item room-card-${status}" data-searchable data-search-text="room ${escapeHtml(roomNum)} ${escapeHtml(room.guestName || '')} ${escapeHtml(roomType)} ${meta.label}">
        <div class="room-card-top">
          <div>
            <span class="room-num">Room ${escapeHtml(roomNum)}</span>
            <span class="room-type-tag">${escapeHtml(roomType)}</span>
          </div>
          <span class="room-status-badge ${meta.badge}">${meta.label}</span>
        </div>
        <p class="guest-name truncate">${escapeHtml(subtitle)}</p>
        <div class="room-card-actions">${actions}</div>
      </div>`;
    })
    .join('');

  bindRoomActions(container);
}

function bindRoomActions(container) {
  container.querySelectorAll('[data-action="check-in"]').forEach((btn) => {
    btn.addEventListener('click', () => openCheckInModal(btn.dataset.room));
  });

  container.querySelectorAll('[data-action="check-out"]').forEach((btn) => {
    btn.addEventListener('click', () => handleCheckOut(btn.dataset.room, btn));
  });

  container.querySelectorAll('[data-action="edit-guest"]').forEach((btn) => {
    btn.addEventListener('click', () => openCheckInModal(btn.dataset.room, true));
  });

  container.querySelectorAll('[data-action="mark-ready"]').forEach((btn) => {
    btn.addEventListener('click', () => handleMarkReady(btn.dataset.room, btn));
  });

  container.querySelectorAll('[data-action="mark-maintenance"]').forEach((btn) => {
    btn.addEventListener('click', () => handleMarkMaintenance(btn.dataset.room, btn));
  });
}

function setupCheckInModal() {
  setupModalClose('check-in-modal', 'check-in-close');

  document.getElementById('check-in-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    btn.textContent = 'Checking in…';

    const roomNumber = normalizeRoom(document.getElementById('check-in-room').value);
    const guestName = document.getElementById('check-in-guest-name').value.trim();
    const guestPhone = document.getElementById('check-in-phone').value.trim();
    const checkOutDate = document.getElementById('check-in-checkout').value;

    if (!roomNumber || !guestName || !checkOutDate) {
      toast('Guest name and check-out date are required', 'error');
      btn.disabled = false;
      btn.textContent = 'Confirm Check-In';
      return;
    }

    try {
      const room = roomsCache.find((r) => String(r.id) === roomNumber);
      const wasVacant = room ? deriveStatus(room) === 'vacant' : true;
      const sessionKey = wasVacant ? newSessionKey(roomNumber) : (room?.sessionKey || newSessionKey(roomNumber));

      if (wasVacant) {
        await flushRoomSession(roomNumber);
      }

      await writeRoom(roomNumber, {
        guestName,
        guestPhone,
        checkOutDate,
        checkInDate: new Date().toISOString().split('T')[0],
        status: 'occupied',
        sessionKey,
        activeOrdersCount: 0,
        activeMessagesCount: 0,
        roomNumber,
        hotelName: 'Ikhsana Hotel',
      });
      toast(`Room ${roomNumber} checked in — Welcome, ${guestName}!`);
      closeModal('check-in-modal');
      e.target.reset();
    } catch (err) {
      toast('Check-in failed', 'error');
      console.error('[Firestore ERROR] Check-in failed:', err);
    } finally {
      btn.disabled = false;
      btn.textContent = 'Confirm Check-In';
    }
  });
}

function setupAddRoomModal() {
  setupModalClose('add-room-modal', 'add-room-close');

  document.getElementById('add-room-btn')?.addEventListener('click', () => {
    document.getElementById('add-room-form')?.reset();
    openModal('add-room-modal');
    document.getElementById('add-room-number')?.focus();
  });

  document.getElementById('add-room-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    btn.textContent = 'Adding…';

    const roomNumber = normalizeRoom(document.getElementById('add-room-number').value);
    const roomType = document.getElementById('add-room-type').value || 'deluxe';

    if (!roomNumber) {
      toast('Room number is required', 'error');
      btn.disabled = false;
      btn.textContent = 'Add Room';
      return;
    }

    if (roomsCache.some((r) => String(r.id) === roomNumber)) {
      toast(`Room ${roomNumber} already exists`, 'error');
      btn.disabled = false;
      btn.textContent = 'Add Room';
      return;
    }

    try {
      await writeRoom(roomNumber, {
        roomNumber,
        roomType,
        status: 'vacant',
        guestName: 'Guest',
        guestPhone: '',
        checkOutDate: '',
        hotelName: 'Ikhsana Hotel',
      });
      toast(`Room ${roomNumber} added`);
      closeModal('add-room-modal');
      e.target.reset();
    } catch (err) {
      toast('Failed to add room', 'error');
      console.error('[Firestore ERROR] Add room failed:', err);
    } finally {
      btn.disabled = false;
      btn.textContent = 'Add Room';
    }
  });
}

function openCheckInModal(roomNumber, isEdit = false) {
  const room = roomsCache.find((r) => String(r.id) === roomNumber);
  document.getElementById('check-in-room').value = roomNumber;
  document.getElementById('check-in-guest-name').value = room?.guestName || '';
  document.getElementById('check-in-phone').value = room?.guestPhone || '';
  document.getElementById('check-in-checkout').value = room?.checkOutDate || '';

  const title = document.getElementById('check-in-modal-title');
  if (title) title.textContent = isEdit ? `Edit Guest — Room ${roomNumber}` : `Check-In — Room ${roomNumber}`;

  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const checkoutInput = document.getElementById('check-in-checkout');
  if (checkoutInput && !checkoutInput.value) {
    checkoutInput.min = new Date().toISOString().split('T')[0];
    checkoutInput.value = tomorrow.toISOString().split('T')[0];
  }

  openModal('check-in-modal');
  document.getElementById('check-in-guest-name')?.focus();
}

async function handleCheckOut(roomNumber, btn) {
  if (!confirm(`Check out guest from Room ${roomNumber}? Room will move to Housekeeping.`)) return;

  btn.disabled = true;
  const prev = btn.textContent;
  btn.textContent = 'Checking out…';

  try {
    await flushRoomSession(roomNumber);
    await writeRoom(roomNumber, {
      guestName: 'Guest',
      guestPhone: '',
      checkOutDate: '',
      checkInDate: '',
      status: 'housekeeping',
      sessionKey: '',
      activeOrdersCount: 0,
      activeMessagesCount: 0,
      cleaned: false,
      checkedOutAt: serverTimestamp(),
      roomNumber,
    });
    toast(`Room ${roomNumber} checked out — sent to housekeeping`);
  } catch (err) {
    toast('Check-out failed', 'error');
    console.error('[Firestore ERROR] Check-out failed:', err);
  } finally {
    btn.disabled = false;
    btn.textContent = prev;
  }
}

async function handleMarkReady(roomNumber, btn) {
  btn.disabled = true;
  const prev = btn.textContent;
  btn.textContent = 'Updating…';

  try {
    await markRoomCleanAndReady(roomNumber);
    toast(`Room ${roomNumber} is vacant and ready for guest`);
  } catch (err) {
    toast('Update failed', 'error');
    console.error('[Firestore ERROR] Mark ready failed:', err);
  } finally {
    btn.disabled = false;
    btn.textContent = prev;
  }
}

async function handleMarkMaintenance(roomNumber, btn) {
  btn.disabled = true;
  const prev = btn.textContent;
  btn.textContent = 'Updating…';

  try {
    await writeRoom(roomNumber, { status: 'maintenance', roomNumber });
    toast(`Room ${roomNumber} marked for maintenance`);
  } catch (err) {
    toast('Update failed', 'error');
    console.error('[Firestore ERROR] Maintenance failed:', err);
  } finally {
    btn.disabled = false;
    btn.textContent = prev;
  }
}

async function writeRoom(roomNumber, data) {
  const docPath = paths.roomDoc(roomNumber);
  const payload = { ...data, updatedAt: serverTimestamp() };
  await setDoc(doc(db, 'Hotels', getHotelId(), 'Rooms', roomNumber), payload, { merge: true });
  logFirestoreWrite('Room PMS', docPath, payload);
}
