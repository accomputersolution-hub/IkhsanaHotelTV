/**
 * Room-wise Feature Control / Card Visibility
 *
 * Path: Hotels/{hotelId}/Rooms/{roomId}
 * Fields (default true when absent — TV treats missing as shown):
 *   showLiveTv, showEntertainment, showDining, showAgenda, showServices, showAlerts
 *
 * Admin toggles write immediately via setDoc({ merge: true }).
 * Scope can be one room or all rooms; "Copy these toggles to all rooms"
 * pushes the selected room's full flag set to every room.
 * Paired TVs listen with onSnapshot on their room document.
 */

import { db } from './firebase-config.js';
import {
  collection,
  doc,
  onSnapshot,
  setDoc,
  writeBatch,
  serverTimestamp,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import { escapeHtml, toast } from './utils.js';
import { logFirestoreWrite, logFirestoreListen, normalizeRoom, formatRoomLabel, paths } from './paths.js';
import { getHotelId, onHotelChange, isCorporateProperty, onHotelMetaChange } from './tenant-context.js';

/** @typedef {{ id: string, roomNumber?: string, [key: string]: any }} RoomDoc */

const BATCH_LIMIT = 400;

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
/** @type {'one' | 'all'} */
let applyScope = 'one';
let writing = false;

export function initRoomFeatures() {
  setupUi();
  listenRooms();
  onHotelChange(() => {
    selectedRoomId = '';
    applyScope = 'one';
    syncScopeRadios();
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

  document.getElementById('room-features-scope')?.addEventListener('change', (e) => {
    const input = e.target;
    if (!(input instanceof HTMLInputElement) || input.type !== 'radio') return;
    applyScope = input.value === 'all' ? 'all' : 'one';
    renderToggles();
  });

  document.getElementById('room-features-apply-all-btn')?.addEventListener('click', () => {
    void copySelectedFlagsToAllRooms();
  });

  document.getElementById('room-features-toggles')?.addEventListener('change', (e) => {
    const input = e.target;
    if (!(input instanceof HTMLInputElement) || input.type !== 'checkbox') return;
    const key = input.dataset.flag;
    if (!key || !selectedRoomId || writing) return;
    void onToggleChange(key, input.checked, input);
  });
}

function syncScopeRadios() {
  document.querySelectorAll('input[name="room-features-scope"]').forEach((el) => {
    if (el instanceof HTMLInputElement) {
      el.checked = el.value === applyScope;
    }
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
  const allLabel = document.getElementById('room-features-scope-all-label');
  const applyAllBtn = document.getElementById('room-features-apply-all-btn');
  if (!select) return;

  if (allLabel) {
    allLabel.textContent = rooms.length ? `All rooms (${rooms.length})` : 'All rooms';
  }

  if (!rooms.length) {
    select.innerHTML = '<option value="">No rooms</option>';
    select.disabled = true;
    empty?.classList.remove('hidden');
    if (applyAllBtn) applyAllBtn.disabled = true;
    return;
  }

  empty?.classList.add('hidden');
  select.disabled = false;
  if (applyAllBtn) applyAllBtn.disabled = !selectedRoomId || rooms.length < 2 || writing;
  select.innerHTML = rooms
    .map((room) => {
      const id = String(room.id);
      const guest = String(room.guestName || '').trim();
      const label =
        guest && guest !== 'Guest'
          ? `${escapeHtml(formatRoomLabel(id))} — ${escapeHtml(guest)}`
          : escapeHtml(formatRoomLabel(id));
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
  const applyAllBtn = document.getElementById('room-features-apply-all-btn');
  if (!container) return;

  syncScopeRadios();

  const room = rooms.find((r) => String(r.id) === selectedRoomId);
  if (!room) {
    container.innerHTML =
      '<p class="empty-state">Select a room to control which home cards appear on that TV.</p>';
    if (status) status.textContent = '';
    if (applyAllBtn) applyAllBtn.disabled = true;
    return;
  }

  if (applyAllBtn) {
    applyAllBtn.disabled = rooms.length < 2 || writing;
  }

  const hotelId = getHotelId();
  if (status) {
    status.textContent =
      applyScope === 'all'
        ? `All ${rooms.length} rooms · Hotels/${hotelId}/Rooms/* · each toggle writes to every room`
        : `Editing Hotels/${hotelId}/Rooms/${selectedRoomId} · changes sync live to the paired TV`;
  }

  container.innerHTML = visibleFlags()
    .map((flag) => {
      const on = flagValue(room, flag.key);
      return `
        <label class="stock-toggle stock-toggle-form room-feature-toggle" data-searchable
          data-search-text="${escapeHtml(flag.label)} ${escapeHtml(flag.key)}">
          <input type="checkbox" data-flag="${escapeHtml(flag.key)}" ${on ? 'checked' : ''} ${writing ? 'disabled' : ''} />
          <span class="stock-slider" aria-hidden="true"></span>
          <span class="stock-toggle-label">
            <strong>${escapeHtml(flag.label)}</strong>
            <span class="room-feature-hint">${escapeHtml(flag.hint)}</span>
          </span>
        </label>`;
    })
    .join('');
}

async function onToggleChange(key, enabled, input) {
  if (applyScope === 'all') {
    const flagMeta = FEATURE_FLAGS.find((f) => f.key === key);
    const label = flagMeta?.label || key;
    const ok = confirm(
      `Apply “${label}” = ${enabled ? 'ON' : 'OFF'} to all ${rooms.length} rooms?\n\nEvery paired TV will update.`,
    );
    if (!ok) {
      input.checked = !enabled;
      return;
    }
    await writeFlagToRooms(
      rooms.map((r) => String(r.id)),
      { [key]: enabled },
      `All rooms: ${label} → ${enabled ? 'shown' : 'hidden'}`,
    );
    if (writing === false && input && document.body.contains(input)) {
      // listener will refresh; keep UI in sync if write failed partially
    }
    return;
  }

  await writeFlag(selectedRoomId, key, enabled, input);
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
    toast(`${formatRoomLabel(roomId)}: ${key} → ${enabled ? 'shown' : 'hidden'}`);
  } catch (err) {
    console.error('[Firestore ERROR] Room feature toggle failed:', err);
    input.checked = !enabled;
    toast('Failed to update feature toggle', 'error');
  }
}

/**
 * @param {string[]} roomIds
 * @param {Record<string, boolean>} flags
 * @param {string} successToast
 */
async function writeFlagToRooms(roomIds, flags, successToast) {
  const hotelId = getHotelId();
  if (!hotelId) {
    toast('No hotel selected', 'error');
    return false;
  }
  if (!roomIds.length) {
    toast('No rooms to update', 'error');
    return false;
  }

  writing = true;
  renderToggles();

  try {
    for (let i = 0; i < roomIds.length; i += BATCH_LIMIT) {
      const chunk = roomIds.slice(i, i + BATCH_LIMIT);
      const batch = writeBatch(db);
      for (const roomId of chunk) {
        const ref = doc(db, 'Hotels', hotelId, 'Rooms', roomId);
        batch.set(
          ref,
          {
            ...flags,
            roomNumber: roomId,
            updatedAt: serverTimestamp(),
          },
          { merge: true },
        );
      }
      await batch.commit();
    }

    for (const roomId of roomIds) {
      const room = rooms.find((r) => String(r.id) === roomId);
      if (!room) continue;
      Object.assign(room, flags);
    }

    logFirestoreWrite('Room Feature Toggle (all)', paths.roomsCollection(), {
      roomCount: roomIds.length,
      ...flags,
    });
    toast(successToast);
    return true;
  } catch (err) {
    console.error('[Firestore ERROR] Bulk room feature toggle failed:', err);
    toast('Failed to update feature toggles for all rooms', 'error');
    return false;
  } finally {
    writing = false;
    renderToggles();
  }
}

async function copySelectedFlagsToAllRooms() {
  if (!selectedRoomId || rooms.length < 2 || writing) return;

  const room = rooms.find((r) => String(r.id) === selectedRoomId);
  if (!room) return;

  const flags = {};
  for (const flag of visibleFlags()) {
    flags[flag.key] = flagValue(room, flag.key);
  }

  const summary = Object.entries(flags)
    .map(([k, v]) => `${k}=${v ? 'ON' : 'OFF'}`)
    .join(', ');
  const ok = confirm(
    `Copy ${formatRoomLabel(selectedRoomId)} feature toggles to all ${rooms.length} rooms?\n\n${summary}`,
  );
  if (!ok) return;

  await writeFlagToRooms(
    rooms.map((r) => String(r.id)),
    flags,
    `Copied toggles from ${formatRoomLabel(selectedRoomId)} to all ${rooms.length} rooms`,
  );
}
