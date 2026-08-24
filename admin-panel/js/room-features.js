/**
 * Room-wise Feature Control / Card Visibility
 *
 * Path: Hotels/{hotelId}/Rooms/{roomId}
 * Fields (default true when absent — TV treats missing as shown):
 *   showLiveTv, showEntertainment, showDining, showAgenda, showServices, showAlerts
 *
 * Admin toggles write immediately via setDoc({ merge: true }).
 * Paired TVs listen with onSnapshot on their room document.
 */

import { db } from './firebase-config.js';
import {
  collection,
  doc,
  onSnapshot,
  setDoc,
  serverTimestamp,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import { escapeHtml, toast } from './utils.js';
import { logFirestoreWrite, logFirestoreListen, normalizeRoom, paths } from './paths.js';
import { getHotelId, onHotelChange, isCorporateProperty, onHotelMetaChange } from './tenant-context.js';

/** @typedef {{ id: string, roomNumber?: string, [key: string]: any }} RoomDoc */

export const FEATURE_FLAGS = Object.freeze([
  {
    key: 'showLiveTv',
    label: 'Live TV',
    hint: 'Home Live TV card',
    hotel: true,
    corporate: true,
  },
  {
    key: 'showEntertainment',
    label: 'Entertainment',
    hint: 'Home Entertainment card',
    hotel: true,
    corporate: true,
  },
  {
    key: 'showDining',
    label: 'Dining / Menu',
    hint: 'In-room dining or pantry menu card',
    hotel: true,
    corporate: true,
  },
  {
    key: 'showAgenda',
    label: 'Daily Agenda',
    hint: 'Corporate agenda card only',
    hotel: false,
    corporate: true,
  },
  {
    key: 'showServices',
    label: 'Services / Helpdesk',
    hint: 'Guest services or emergency contacts card',
    hotel: true,
    corporate: true,
  },
  {
    key: 'showAlerts',
    label: 'Alerts',
    hint: 'Hotel alerts card (hotel flavor)',
    hotel: true,
    corporate: false,
  },
]);

/** @type {RoomDoc[]} */
let rooms = [];
/** @type {(() => void) | null} */
let roomsUnsub = null;
/** @type {string} */
let selectedRoomId = '';

export function initRoomFeatures() {
  setupUi();
  listenRooms();
  onHotelChange(() => {
    selectedRoomId = '';
    listenRooms();
  });
  onHotelMetaChange(() => renderToggles());
}

function setupUi() {
  const select = document.getElementById('room-features-select');
  select?.addEventListener('change', () => {
    selectedRoomId = normalizeRoom(select.value);
    renderToggles();
  });

  document.getElementById('room-features-toggles')?.addEventListener('change', (e) => {
    const input = e.target;
    if (!(input instanceof HTMLInputElement) || input.type !== 'checkbox') return;
    const key = input.dataset.flag;
    if (!key || !selectedRoomId) return;
    void writeFlag(selectedRoomId, key, input.checked, input);
  });
}

function listenRooms() {
  if (roomsUnsub) {
    roomsUnsub();
    roomsUnsub = null;
  }
  rooms = [];
  selectedRoomId = '';
  renderRoomSelect();
  renderToggles();

  const hotelId = getHotelId();
  if (!hotelId) return;

  const colPath = paths.roomsCollection();
  logFirestoreListen('Room Features', colPath);
  roomsUnsub = onSnapshot(
    collection(db, 'Hotels', hotelId, 'Rooms'),
    (snapshot) => {
      rooms = snapshot.docs
        .map((d) => ({ id: d.id, ...d.data() }))
        .sort((a, b) => String(a.id).localeCompare(String(b.id), undefined, { numeric: true }));
      if (selectedRoomId && !rooms.some((r) => String(r.id) === selectedRoomId)) {
        selectedRoomId = '';
      }
      if (!selectedRoomId && rooms.length) {
        selectedRoomId = String(rooms[0].id);
      }
      renderRoomSelect();
      renderToggles();
    },
    (err) => {
      console.error('[Firestore ERROR] Room features listener:', err);
      toast('Failed to load rooms for feature toggles', 'error');
    },
  );
}

function renderRoomSelect() {
  const select = document.getElementById('room-features-select');
  const empty = document.getElementById('room-features-empty');
  if (!select) return;

  if (!rooms.length) {
    select.innerHTML = '<option value="">No rooms</option>';
    select.disabled = true;
    empty?.classList.remove('hidden');
    return;
  }

  empty?.classList.add('hidden');
  select.disabled = false;
  select.innerHTML = rooms
    .map((room) => {
      const id = String(room.id);
      const guest = String(room.guestName || '').trim();
      const label =
        guest && guest !== 'Guest'
          ? `Room ${escapeHtml(id)} — ${escapeHtml(guest)}`
          : `Room ${escapeHtml(id)}`;
      const selected = id === selectedRoomId ? ' selected' : '';
      return `<option value="${escapeHtml(id)}"${selected}>${label}</option>`;
    })
    .join('');
}

function flagValue(room, key) {
  if (!room) return true;
  const nested = room.features && typeof room.features === 'object' ? room.features : null;
  const raw = room[key] ?? nested?.[key];
  if (raw === undefined || raw === null) return true;
  if (typeof raw === 'boolean') return raw;
  if (typeof raw === 'number') return raw !== 0;
  const s = String(raw).trim().toLowerCase();
  if (s === 'false' || s === '0' || s === 'no') return false;
  if (s === 'true' || s === '1' || s === 'yes') return true;
  return true;
}

function visibleFlags() {
  const corporate = isCorporateProperty();
  return FEATURE_FLAGS.filter((f) => (corporate ? f.corporate : f.hotel));
}

function renderToggles() {
  const container = document.getElementById('room-features-toggles');
  const status = document.getElementById('room-features-status');
  if (!container) return;

  const room = rooms.find((r) => String(r.id) === selectedRoomId);
  if (!room) {
    container.innerHTML =
      '<p class="empty-state">Select a room to control which home cards appear on that TV.</p>';
    if (status) status.textContent = '';
    return;
  }

  if (status) {
    status.textContent = `Editing Hotels/${getHotelId()}/Rooms/${selectedRoomId} · changes sync live to the paired TV`;
  }

  container.innerHTML = visibleFlags()
    .map((flag) => {
      const on = flagValue(room, flag.key);
      return `
        <label class="stock-toggle stock-toggle-form room-feature-toggle" data-searchable
          data-search-text="${escapeHtml(flag.label)} ${escapeHtml(flag.key)}">
          <input type="checkbox" data-flag="${escapeHtml(flag.key)}" ${on ? 'checked' : ''} />
          <span class="stock-slider" aria-hidden="true"></span>
          <span class="stock-toggle-label">
            <strong>${escapeHtml(flag.label)}</strong>
            <span class="room-feature-hint">${escapeHtml(flag.hint)}</span>
          </span>
        </label>`;
    })
    .join('');
}

async function writeFlag(roomId, key, enabled, input) {
  const hotelId = getHotelId();
  if (!hotelId) {
    toast('No hotel selected', 'error');
    input.checked = !enabled;
    return;
  }

  const payload = {
    [key]: enabled,
    roomNumber: roomId,
    updatedAt: serverTimestamp(),
  };
  const docPath = paths.roomDoc(roomId);

  try {
    await setDoc(doc(db, 'Hotels', hotelId, 'Rooms', roomId), payload, { merge: true });
    logFirestoreWrite('Room Feature Toggle', docPath, payload);
    const room = rooms.find((r) => String(r.id) === roomId);
    if (room) room[key] = enabled;
    toast(`Room ${roomId}: ${key} → ${enabled ? 'shown' : 'hidden'}`);
  } catch (err) {
    console.error('[Firestore ERROR] Room feature toggle failed:', err);
    input.checked = !enabled;
    toast('Failed to update feature toggle', 'error');
  }
}
