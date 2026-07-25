import { db } from './firebase-config.js';
import {
  collection,
  doc,
  addDoc,
  updateDoc,
  onSnapshot,
  serverTimestamp,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import {
  escapeHtml,
  formatTime,
  toast,
  playServiceChime,
  hideConnectionError,
  showConnectionError,
} from './utils.js';
import { normalizeRoom, paths, logFirestoreWrite, logFirestoreListen } from './paths.js';
import { writeRoomAlert } from './alerts.js';
import { markRoomCleanAndReady } from './guests.js';
import { getHotelId, onHotelChange } from './tenant-context.js';

const CLEANING_STATUSES = new Set(['housekeeping', 'maintenance', 'needs_cleaning']);

const STATUS_LABELS = {
  pending: 'Pending',
  in_progress: 'In Progress',
  completed: 'Completed',
  cancelled: 'Cancelled',
};

const STATUS_BADGES = {
  pending: 'req-status-pending',
  in_progress: 'req-status-progress',
  completed: 'req-status-completed',
  cancelled: 'req-status-cancelled',
};

const HOUSEKEEPING_TYPES = {
  housekeeping: 'Room Cleaning',
  extra_towels: 'Extra Towels & Linen',
  amenities: 'Bottled Water / Amenities',
  laundry: 'Laundry Pickup',
  turndown: 'Turndown Service',
  maintenance: 'Maintenance',
};

const CONCIERGE_TYPES = {
  concierge_call: 'Concierge / Front Desk Call',
  cab_booking: 'Cab Booking',
  airport_transfer: 'Airport Transfer',
  local_tour: 'Local Tour',
  restaurant_booking: 'Restaurant Booking',
  wake_up_call: 'Wake-up Call',
};

const listeners = {};
let hkView = 'guest_requests';
let hkCleaningRooms = [];
let hkReadyTodayCount = 0;
let cleaningRoomsInitialized = false;
let knownCleaningRoomIds = new Set();
let cleaningRoomsUnsub = null;

export function initHousekeeping() {
  initRequestsModule({
    department: 'housekeeping',
    listId: 'housekeeping-list',
    countId: 'housekeeping-count',
    filterId: 'housekeeping-filters',
    statPendingId: 'hk-stat-pending',
    statProgressId: 'hk-stat-progress',
    statCompletedId: 'hk-stat-completed',
  });
  setupHousekeepingViewTabs();
  onHotelChange(() => {
    knownCleaningRoomIds = new Set();
    cleaningRoomsInitialized = false;
    listenCleaningRooms();
  });
}

export function initConcierge() {
  initRequestsModule({
    department: 'concierge',
    listId: 'concierge-list',
    countId: 'concierge-count',
    filterId: 'concierge-filters',
    statPendingId: 'con-stat-pending',
    statProgressId: 'con-stat-progress',
    statCompletedId: 'con-stat-completed',
  });
}

/** Demo helper for Sim Request quick action */
export async function createTestRequest(department = 'housekeeping') {
  const types = department === 'concierge' ? CONCIERGE_TYPES : HOUSEKEEPING_TYPES;
  const serviceType = Object.keys(types)[Math.floor(Math.random() * Object.keys(types).length)];
  const roomNumber = String(101 + Math.floor(Math.random() * 8));

  const payload = {
    roomNumber,
    guestName: 'Demo Guest',
    department,
    serviceType,
    serviceLabel: types[serviceType],
    status: 'pending',
    source: 'admin_sim',
    timestamp: Date.now(),
    createdAt: serverTimestamp(),
  };

  const ref = await addDoc(collection(db, 'Hotels', getHotelId(), 'Requests'), payload);
  logFirestoreWrite('Sim Request', `${paths.requestsCollection()}/${ref.id}`, payload);
  return ref.id;
}

function setupHousekeepingViewTabs() {
  const container = document.getElementById('hk-view-tabs');
  if (!container) return;

  container.querySelectorAll('[data-hk-view]').forEach((tab) => {
    tab.addEventListener('click', () => {
      hkView = tab.dataset.hkView;
      container.querySelectorAll('[data-hk-view]').forEach((t) => {
        t.classList.toggle('active', t.dataset.hkView === hkView);
      });
      document.getElementById('hk-guest-panel')?.classList.toggle('hidden', hkView !== 'guest_requests');
      document.getElementById('hk-rooms-panel')?.classList.toggle('hidden', hkView !== 'room_cleaning');
      updateHousekeepingHeaderCount();
    });
  });
}

function listenCleaningRooms() {
  if (cleaningRoomsUnsub) {
    cleaningRoomsUnsub();
    cleaningRoomsUnsub = null;
  }

  const hotelId = getHotelId();
  if (!hotelId) {
    hkCleaningRooms = [];
    hkReadyTodayCount = 0;
    updateCleaningRoomStats();
    renderCleaningRooms();
    updateHousekeepingHeaderCount();
    updateHousekeepingTabBadges();
    updateSidebarHousekeepingBadge();
    return;
  }

  logFirestoreListen('Cleaning Rooms', paths.roomsCollection());

  cleaningRoomsUnsub = onSnapshot(
    collection(db, 'Hotels', hotelId, 'Rooms'),
    (snapshot) => {
      const allRooms = snapshot.docs.map((d) => ({ id: d.id, ...d.data() }));

      snapshot.docChanges().forEach((change) => {
        if (change.type === 'added' && cleaningRoomsInitialized) {
          const data = change.doc.data();
          const roomId = change.doc.id;
          if (CLEANING_STATUSES.has(data.status || '') && !knownCleaningRoomIds.has(roomId)) {
            playServiceChime();
            showCleaningBanner(roomId);
          }
        }
      });

      hkCleaningRooms = allRooms
        .filter((room) => CLEANING_STATUSES.has(room.status || ''))
        .sort((a, b) => toMillis(b.checkedOutAt || b.updatedAt) - toMillis(a.checkedOutAt || a.updatedAt));

      hkReadyTodayCount = allRooms.filter(
        (room) => room.status === 'vacant' && room.cleaned && isToday(room.cleanedAt || room.updatedAt),
      ).length;

      knownCleaningRoomIds = new Set(hkCleaningRooms.map((r) => String(r.id)));
      cleaningRoomsInitialized = true;

      updateCleaningRoomStats();
      renderCleaningRooms();
      updateHousekeepingHeaderCount();
      updateHousekeepingTabBadges();
      updateSidebarHousekeepingBadge();
    },
    (err) => {
      console.error('[Firestore ERROR] Cleaning rooms listener:', err);
    },
  );
}

function showCleaningBanner(roomNumber) {
  const banner = document.getElementById('new-order-banner');
  if (!banner) return;
  banner.innerHTML = `🛏️ Room <strong>${escapeHtml(String(roomNumber))}</strong> checked out — added to cleaning queue`;
  banner.classList.remove('hidden');
  setTimeout(() => banner.classList.add('hidden'), 6000);
}

function toMillis(ts) {
  if (!ts) return 0;
  if (typeof ts.toMillis === 'function') return ts.toMillis();
  if (typeof ts === 'number') return ts;
  const d = new Date(ts);
  return Number.isNaN(d.getTime()) ? 0 : d.getTime();
}

function isToday(ts) {
  const ms = toMillis(ts);
  if (!ms) return false;
  const d = new Date(ms);
  const today = new Date();
  return d.toDateString() === today.toDateString();
}

function updateCleaningRoomStats() {
  const housekeeping = hkCleaningRooms.filter((r) => r.status === 'housekeeping' || r.status === 'needs_cleaning').length;
  const maintenance = hkCleaningRooms.filter((r) => r.status === 'maintenance').length;
  setText('hk-stat-cleaning', housekeeping);
  setText('hk-stat-maintenance', maintenance);
  setText('hk-stat-ready', hkReadyTodayCount);
}

function updateHousekeepingTabBadges() {
  const state = listeners.housekeeping;
  const guestActive = state
    ? state.allRequests.filter((r) => r.status !== 'cancelled' && r.status !== 'completed').length
    : 0;
  const roomCount = hkCleaningRooms.length;

  setText('hk-tab-guest-count', guestActive);
  setText('hk-tab-room-count', roomCount);

  document.getElementById('hk-tab-guest-count')?.classList.toggle('has-items', guestActive > 0);
  document.getElementById('hk-tab-room-count')?.classList.toggle('has-items', roomCount > 0);
}

function updateSidebarHousekeepingBadge() {
  const state = listeners.housekeeping;
  const guestActive = state
    ? state.allRequests.filter((r) => r.status === 'pending').length
    : 0;
  const roomCount = hkCleaningRooms.filter((r) => r.status === 'housekeeping' || r.status === 'needs_cleaning').length;
  const total = guestActive + roomCount;
  const badge = document.getElementById('nav-hk-badge');
  if (!badge) return;
  badge.textContent = total;
  badge.classList.toggle('hidden', total === 0);
}

function updateHousekeepingHeaderCount() {
  const state = listeners.housekeeping;
  const guestActive = state
    ? state.allRequests.filter((r) => r.status !== 'cancelled' && r.status !== 'completed').length
    : 0;
  const roomCount = hkCleaningRooms.length;
  const total = hkView === 'room_cleaning' ? roomCount : guestActive + roomCount;
  setText('housekeeping-count', total);
  updateHousekeepingTabBadges();
  updateSidebarHousekeepingBadge();
}

function renderCleaningRooms() {
  const container = document.getElementById('hk-room-cleaning-list');
  if (!container) return;

  if (!hkCleaningRooms.length) {
    container.innerHTML = `
      <p class="empty-state col-span-full">
        No rooms awaiting turnover. Checked-out rooms appear here automatically.
      </p>`;
    return;
  }

  container.innerHTML = hkCleaningRooms.map(renderCleaningRoomCard).join('');

  container.querySelectorAll('[data-action="mark-cleaned"]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const roomNumber = btn.dataset.room;
      btn.disabled = true;
      const prev = btn.textContent;
      btn.textContent = 'Updating…';
      try {
        await markRoomCleanAndReady(roomNumber);
        logFirestoreWrite('Room Cleaned', paths.roomDoc(roomNumber), { status: 'vacant', cleaned: true });
        toast(`Room ${roomNumber} marked Cleaned & Ready — available for check-in`);
      } catch (err) {
        toast('Failed to update room', 'error');
        console.error('[Firestore ERROR] Mark cleaned failed:', err);
        btn.disabled = false;
        btn.textContent = prev;
      }
    });
  });
}

function renderCleaningRoomCard(room) {
  const roomNum = String(room.id);
  const status = room.status || 'housekeeping';
  const statusLabel =
    status === 'maintenance' ? 'Maintenance' : status === 'needs_cleaning' ? 'Needs Cleaning' : 'Needs Cleaning';
  const roomType = room.roomType === 'suite' ? 'Suite' : 'Deluxe Room';
  const turnoverNote =
    status === 'maintenance'
      ? 'Maintenance hold — inspect before releasing'
      : 'Guest checked out — awaiting room turnover';

  return `
    <div class="cleaning-room-card" data-searchable data-search-text="room ${escapeHtml(roomNum)} ${escapeHtml(statusLabel)} ${escapeHtml(roomType)}">
      <div class="service-card-top">
        <div class="service-card-icon">🛏️</div>
        <div class="service-card-info">
          <div class="service-card-title-row">
            <span class="service-room">Room ${escapeHtml(roomNum)}</span>
            <span class="req-status-badge req-status-pending">${escapeHtml(statusLabel)}</span>
          </div>
          <p class="service-type">${escapeHtml(roomType)} · ${escapeHtml(turnoverNote)}</p>
          <p class="service-meta">${room.cleaned === false ? 'Not cleaned' : 'Awaiting clean'} · Checked out ${formatTime(room.checkedOutAt || room.updatedAt || room.timestamp)}</p>
        </div>
      </div>
      <div class="service-card-actions">
        <button data-action="mark-cleaned" data-room="${escapeHtml(roomNum)}" class="room-action-btn room-action-primary">
          Mark as Cleaned &amp; Ready
        </button>
      </div>
    </div>`;
}

function initRequestsModule(config) {
  const state = {
    ...config,
    allRequests: [],
    activeFilter: 'all',
    knownIds: new Set(),
    initialized: false,
    unsub: null,
  };
  listeners[config.department] = state;

  setupFilterTabs(state);
  onHotelChange(() => {
    state.knownIds = new Set();
    state.initialized = false;
    listenRequests(state);
  });
}

function setupFilterTabs(state) {
  const container = document.getElementById(state.filterId);
  if (!container) return;

  const tabs = [
    { key: 'all', label: 'All' },
    { key: 'pending', label: 'Pending' },
    { key: 'in_progress', label: 'In Progress' },
    { key: 'completed', label: 'Completed' },
  ];

  container.innerHTML = tabs
    .map(
      (tab) =>
        `<button class="filter-tab${state.activeFilter === tab.key ? ' active' : ''}" data-filter="${tab.key}">${tab.label}</button>`,
    )
    .join('');

  container.querySelectorAll('.filter-tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      state.activeFilter = tab.dataset.filter;
      setupFilterTabs(state);
      renderRequests(state);
    });
  });
}

function listenRequests(state) {
  if (state.unsub) {
    state.unsub();
    state.unsub = null;
  }

  const hotelId = getHotelId();
  if (!hotelId) {
    state.allRequests = [];
    updateStats(state);
    renderRequests(state);
    return;
  }

  logFirestoreListen(`${state.department} Requests`, paths.requestsCollection());

  state.unsub = onSnapshot(
    collection(db, 'Hotels', hotelId, 'Requests'),
    (snapshot) => {
      hideConnectionError();

      snapshot.docChanges().forEach((change) => {
        if (change.type === 'added') {
          const data = change.doc.data();
          if (
            state.initialized &&
            !state.knownIds.has(change.doc.id) &&
            data.department === state.department &&
            data.status === 'pending'
          ) {
            playServiceChime();
            showRequestBanner(state.department, data);
          }
          state.knownIds.add(change.doc.id);
        }
      });

      state.initialized = true;
      state.allRequests = snapshot.docs
        .map((d) => ({ id: d.id, ...d.data() }))
        .filter((r) => r.department === state.department)
        .sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0));

      updateStats(state);
      renderRequests(state);
    },
    (err) => {
      console.error('[Firestore ERROR] Requests listener:', err);
      showConnectionError('Could not load service requests from Firestore.');
    },
  );
}

function showRequestBanner(department, data) {
  const banner = document.getElementById('new-order-banner');
  if (!banner) return;
  const deptLabel = department === 'concierge' ? 'Concierge' : 'Housekeeping';
  banner.innerHTML = `New ${deptLabel} request — Room <strong>${escapeHtml(String(data.roomNumber || '?'))}</strong> · ${escapeHtml(data.serviceLabel || 'Service')}`;
  banner.classList.remove('hidden');
  setTimeout(() => banner.classList.add('hidden'), 6000);
}

function updateStats(state) {
  const pending = state.allRequests.filter((r) => r.status === 'pending').length;
  const progress = state.allRequests.filter((r) => r.status === 'in_progress').length;
  const completed = state.allRequests.filter((r) => r.status === 'completed').length;

  setText(state.statPendingId, pending);
  setText(state.statProgressId, progress);
  setText(state.statCompletedId, completed);
  if (state.department === 'housekeeping') {
    updateHousekeepingHeaderCount();
  } else {
    setText(state.countId, state.allRequests.filter((r) => r.status !== 'cancelled').length);
  }
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

function getFilteredRequests(state) {
  if (state.activeFilter === 'all') {
    return state.allRequests.filter((r) => r.status !== 'cancelled');
  }
  return state.allRequests.filter((r) => r.status === state.activeFilter);
}

function renderRequests(state) {
  const container = document.getElementById(state.listId);
  if (!container) return;

  const requests = getFilteredRequests(state);

  if (!state.allRequests.length) {
    container.innerHTML = `<p class="empty-state col-span-full">No ${state.department} requests yet. Guest TVs can submit from Guest Services.</p>`;
    return;
  }

  if (!requests.length) {
    container.innerHTML = `<p class="empty-state col-span-full">No requests match this filter.</p>`;
    return;
  }

  container.innerHTML = requests.map((req) => renderRequestCard(req, state.department)).join('');
  bindRequestActions(container, state.department);
}

function renderRequestCard(req, department) {
  const status = req.status || 'pending';
  const badge = STATUS_BADGES[status] || STATUS_BADGES.pending;
  const icon = department === 'concierge' ? '🚖' : '🧹';

  let actions = '';
  if (status === 'pending') {
    actions = `
      <button data-action="accept" data-id="${req.id}" class="room-action-btn room-action-primary">Accept Request</button>
      <button data-action="cancel" data-id="${req.id}" class="room-action-btn room-action-danger">Cancel</button>`;
  } else if (status === 'in_progress') {
    actions = `
      <button data-action="complete" data-id="${req.id}" class="room-action-btn room-action-primary">Mark Completed</button>
      <button data-action="cancel" data-id="${req.id}" class="room-action-btn room-action-danger">Cancel</button>`;
  }

  return `
    <div class="service-card service-card-${status}" data-searchable data-search-text="room ${escapeHtml(String(req.roomNumber || ''))} ${escapeHtml(req.serviceLabel || '')} ${escapeHtml(req.guestName || '')}">
      <div class="service-card-top">
        <div class="service-card-icon">${icon}</div>
        <div class="service-card-info">
          <div class="service-card-title-row">
            <span class="service-room">Room ${escapeHtml(String(req.roomNumber || '—'))}</span>
            <span class="req-status-badge ${badge}">${STATUS_LABELS[status] || status}</span>
          </div>
          <p class="service-type">${escapeHtml(req.serviceLabel || req.serviceType || 'Service Request')}</p>
          <p class="service-meta">${escapeHtml(req.guestName || 'Guest')} · ${formatTime(req.timestamp)}</p>
        </div>
      </div>
      ${actions ? `<div class="service-card-actions">${actions}</div>` : ''}
    </div>`;
}

function bindRequestActions(container, department) {
  container.querySelectorAll('[data-action="accept"]').forEach((btn) => {
    btn.addEventListener('click', () => updateRequestStatus(btn, department, 'in_progress'));
  });
  container.querySelectorAll('[data-action="complete"]').forEach((btn) => {
    btn.addEventListener('click', () => updateRequestStatus(btn, department, 'completed'));
  });
  container.querySelectorAll('[data-action="cancel"]').forEach((btn) => {
    btn.addEventListener('click', () => {
      if (!confirm('Cancel this service request?')) return;
      updateRequestStatus(btn, department, 'cancelled');
    });
  });
}

async function updateRequestStatus(btn, department, newStatus) {
  const id = btn.dataset.id;
  const state = listeners[department];
  const request = state?.allRequests.find((r) => r.id === id);
  if (!request) return;

  btn.disabled = true;
  const prev = btn.textContent;
  btn.textContent = 'Updating…';

  try {
    await updateDoc(doc(db, 'Hotels', getHotelId(), 'Requests', id), {
      status: newStatus,
      updatedAt: serverTimestamp(),
    });
    logFirestoreWrite('Request Status', `${paths.requestsCollection()}/${id}`, { status: newStatus });

    if (newStatus === 'in_progress' || newStatus === 'completed') {
      await notifyGuestOnTv(request, department, newStatus);
    }

    const labels = {
      in_progress: 'Request accepted',
      completed: 'Request completed',
      cancelled: 'Request cancelled',
    };
    toast(labels[newStatus] || 'Status updated');
  } catch (err) {
    toast('Failed to update request', 'error');
    console.error('[Firestore ERROR] Request update failed:', err);
    btn.disabled = false;
    btn.textContent = prev;
  }
}

async function notifyGuestOnTv(request, department, status) {
  const room = normalizeRoom(request.roomNumber);
  const service = request.serviceLabel || request.serviceType || 'service';
  let title;
  let message;

  if (department === 'housekeeping') {
    if (status === 'in_progress') {
      title = 'Housekeeping Update';
      message = `Housekeeping staff is on the way to Room ${room}.`;
    } else {
      title = 'Housekeeping Complete';
      message = `Your ${service} request has been completed. Thank you!`;
    }
  } else {
    if (status === 'in_progress') {
      title = 'Concierge Update';
      message = `Concierge is handling your ${service} request for Room ${room}.`;
    } else {
      title = 'Concierge Complete';
      message = `Your ${service} request has been completed. Enjoy your stay!`;
    }
  }

  await writeRoomAlert({
    roomNumber: room,
    title,
    message,
    priority: status === 'in_progress' ? 'normal' : 'normal',
    source: 'service_request',
    alertType: department,
    durationMs: 30000,
    orderId: request.id,
  });
}
