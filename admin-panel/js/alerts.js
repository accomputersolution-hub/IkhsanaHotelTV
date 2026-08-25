import { db, rtdb } from './firebase-config.js';
import {
  collection,
  doc,
  addDoc,
  getDocs,
  getDoc,
  updateDoc,
  onSnapshot,
  query,
  where,
  orderBy,
  writeBatch,
  serverTimestamp,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import { ref as rtdbRef, set as rtdbSet } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-database.js';
import { escapeHtml, formatTime, toast, showConnectionError, hideConnectionError } from './utils.js';
import { normalizeRoom, paths, logFirestoreWrite, logFirestoreListen } from './paths.js';
import { getHotelId, onHotelChange } from './tenant-context.js';
import { normalizeHotelId } from './firebase-config.js';

const ALERT_PRESETS = {
  emergency: {
    title: 'Emergency Alert',
    message: 'Please remain in your room and follow staff instructions immediately.',
    alertType: 'emergency',
    duration: '0',
  },
  event: {
    title: 'Event Notice',
    message: 'Join us for a special event at the Main Dining Hall this evening.',
    alertType: 'event',
    duration: '60000',
  },
  promotional: {
    title: 'Special Offer',
    message: 'Exclusive spa and dining offers are available for in-house guests today.',
    alertType: 'promotional',
    duration: '30000',
  },
  checkout: {
    title: 'Checkout Reminder',
    message: 'Checkout is at 11:00 AM. Please contact the front desk for late checkout requests.',
    alertType: 'checkout',
    duration: '60000',
  },
};

const DURATION_LABELS = {
  10000: '10s',
  30000: '30s',
  60000: '1 Min',
  0: 'Persistent',
};

const TYPE_LABELS = {
  emergency: 'Emergency',
  event: 'Event Notice',
  promotional: 'Promotional',
  checkout: 'Checkout',
};

let broadcastsCache = [];
let activePreset = null;
let broadcastsUnsub = null;

/**
 * Shared alert writer — used by broadcasts, manual alerts, and order-status notifications.
 */
export async function writeRoomAlert({
  roomNumber,
  title,
  message,
  priority = 'normal',
  source = 'manual',
  orderId = null,
  broadcastId = null,
  alertType = null,
  durationMs = null,
}) {
  const payload = {
    roomNumber: normalizeRoom(roomNumber),
    title,
    message,
    priority,
    read: false,
    revoked: false,
    timestamp: Date.now(),
    createdAt: serverTimestamp(),
    source,
    ...(orderId ? { orderId } : {}),
    ...(broadcastId ? { broadcastId } : {}),
    ...(alertType ? { alertType } : {}),
    ...(durationMs != null ? { durationMs } : {}),
  };

  const collectionPath = paths.alertsCollection();
  const docRef = await addDoc(collection(db, 'Hotels', getHotelId(), 'Alerts'), payload);
  logFirestoreWrite(`Alert (${source})`, `${collectionPath}/${docRef.id}`, payload);
  return docRef.id;
}

export function initAlerts() {
  setupBroadcastForm();
  setupPresetButtons();
  setupClearActiveButton();
  setupTvPreview();
  setupOverlayPopupsToggle();
  onHotelChange(() => {
    listenBroadcastHistory();
    loadOverlayPopupsToggle();
  });
}

function syncOverlayToggleLabel(enabled) {
  const label = document.getElementById('allow-overlay-popups-label');
  if (!label) return;
  label.textContent = enabled ? 'Enabled' : 'Disabled';
  label.classList.toggle('is-inactive', !enabled);
}

function mirrorOverlayPopupsToRtdb(hotelId, allowOverlayPopups) {
  const id = normalizeHotelId(hotelId);
  Promise.resolve()
    .then(async () => {
      const base = `hotels/${id}/config`;
      await Promise.all([
        rtdbSet(rtdbRef(rtdb, `${base}/allowOverlayPopups`), allowOverlayPopups),
        rtdbSet(rtdbRef(rtdb, `${base}/allow_overlay_popups`), allowOverlayPopups),
      ]);
      console.log('[alerts] RTDB overlay mirror OK →', base, allowOverlayPopups);
    })
    .catch((err) => {
      console.warn('[alerts] RTDB overlay mirror skipped', err);
    });
}

async function loadOverlayPopupsToggle() {
  const toggle = document.getElementById('allow-overlay-popups-toggle');
  if (!toggle) return;
  const hotelId = getHotelId();
  if (!hotelId) {
    toggle.checked = true;
    syncOverlayToggleLabel(true);
    return;
  }
  try {
    const snap = await getDoc(doc(db, 'Hotels', hotelId));
    const data = snap.exists() ? snap.data() || {} : {};
    const enabled =
      typeof data.allowOverlayPopups === 'boolean'
        ? data.allowOverlayPopups
        : typeof data.allow_overlay_popups === 'boolean'
          ? data.allow_overlay_popups
          : true;
    toggle.checked = enabled;
    syncOverlayToggleLabel(enabled);
  } catch (err) {
    console.warn('[alerts] loadOverlayPopupsToggle failed', err);
    toggle.checked = true;
    syncOverlayToggleLabel(true);
  }
}

function setupOverlayPopupsToggle() {
  const toggle = document.getElementById('allow-overlay-popups-toggle');
  if (!toggle) return;

  toggle.addEventListener('change', async () => {
    const hotelId = getHotelId();
    if (!hotelId) {
      toast('Select a hotel first', 'error');
      toggle.checked = !toggle.checked;
      syncOverlayToggleLabel(toggle.checked);
      return;
    }
    const enabled = Boolean(toggle.checked);
    syncOverlayToggleLabel(enabled);
    toggle.disabled = true;
    try {
      await updateDoc(doc(db, 'Hotels', hotelId), {
        allowOverlayPopups: enabled,
        allow_overlay_popups: enabled,
        updatedAt: serverTimestamp(),
      });
      logFirestoreWrite('Overlay Popups Toggle', `Hotels/${hotelId}`, {
        allowOverlayPopups: enabled,
      });
      mirrorOverlayPopupsToRtdb(hotelId, enabled);
      toast(
        enabled
          ? 'Global overlay popups enabled on TVs'
          : 'Global overlay popups disabled — in-app only',
      );
    } catch (err) {
      console.error('[alerts] allowOverlayPopups update failed', err);
      toggle.checked = !enabled;
      syncOverlayToggleLabel(!enabled);
      toast(err.message || 'Failed to update overlay setting', 'error');
    } finally {
      toggle.disabled = false;
    }
  });

  loadOverlayPopupsToggle();
}

function setupTvPreview() {
  const titleInput = document.getElementById('broadcast-title');
  const messageInput = document.getElementById('broadcast-message');
  const titleEl = document.getElementById('tv-preview-title');
  const bodyEl = document.getElementById('tv-preview-body');
  if (!titleInput || !messageInput || !titleEl || !bodyEl) return;

  const sync = () => {
    titleEl.textContent = titleInput.value.trim() || 'Message Title';
    bodyEl.textContent =
      messageInput.value.trim() || 'Your broadcast copy will appear here on guest screens.';
  };
  titleInput.addEventListener('input', sync);
  messageInput.addEventListener('input', sync);
  sync();
}

function setupBroadcastForm() {
  const form = document.getElementById('broadcast-form');
  const roomsInput = document.getElementById('broadcast-rooms');

  document.querySelectorAll('input[name="broadcast-target"]').forEach((radio) => {
    radio.addEventListener('change', () => {
      const isSpecific = document.querySelector('input[name="broadcast-target"]:checked')?.value === 'specific';
      if (roomsInput) {
        roomsInput.disabled = !isSpecific;
        roomsInput.required = isSpecific;
      }
    });
  });

  form?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = form.querySelector('button[type="submit"]');
    btn.disabled = true;
    btn.textContent = 'Sending…';

    const title = document.getElementById('broadcast-title').value.trim();
    const message = document.getElementById('broadcast-message').value.trim();
    const targetMode = document.querySelector('input[name="broadcast-target"]:checked')?.value || 'all';
    const durationMs = parseInt(document.getElementById('broadcast-duration').value, 10);
    const alertType = document.getElementById('broadcast-type').value;
    const priority = alertType === 'emergency' ? 'urgent' : 'normal';

    if (!title || !message) {
      toast('Title and message are required', 'error');
      btn.disabled = false;
      btn.textContent = 'Send Broadcast';
      return;
    }

    try {
      let targetRooms = [];
      if (targetMode === 'all') {
        targetRooms = await fetchAllRoomNumbers();
      } else {
        targetRooms = parseRoomList(document.getElementById('broadcast-rooms').value);
      }

      if (!targetRooms.length) {
        toast('No target rooms found. Add rooms in PMS first.', 'error');
        btn.disabled = false;
        btn.textContent = 'Send Broadcast';
        return;
      }

      const broadcastPayload = {
        title,
        message,
        alertType,
        durationMs,
        priority,
        targetMode,
        targetRooms,
        roomCount: targetRooms.length,
        status: 'delivered',
        timestamp: Date.now(),
        createdAt: serverTimestamp(),
      };

      const broadcastRef = await addDoc(
        collection(db, 'Hotels', getHotelId(), 'Broadcasts'),
        broadcastPayload,
      );
      logFirestoreWrite('Broadcast', `${paths.broadcastsCollection()}/${broadcastRef.id}`, broadcastPayload);

      await deliverBroadcastToRooms({
        broadcastId: broadcastRef.id,
        targetRooms,
        title,
        message,
        priority,
        alertType,
        durationMs,
      });

      toast(`Broadcast sent to ${targetRooms.length} room(s)`);
      form.reset();
      document.querySelector('input[name="broadcast-target"][value="all"]').checked = true;
      if (roomsInput) {
        roomsInput.disabled = true;
        roomsInput.required = false;
      }
      clearPresetSelection();
    } catch (err) {
      toast('Failed to send broadcast', 'error');
      console.error('[Firestore ERROR] Broadcast failed:', err);
    } finally {
      btn.disabled = false;
      btn.textContent = 'Send Broadcast';
    }
  });
}

async function deliverBroadcastToRooms({ broadcastId, targetRooms, title, message, priority, alertType, durationMs }) {
  const batchSize = 400;
  for (let i = 0; i < targetRooms.length; i += batchSize) {
    const chunk = targetRooms.slice(i, i + batchSize);
    const batch = writeBatch(db);

    chunk.forEach((roomNumber) => {
      const alertRef = doc(collection(db, 'Hotels', getHotelId(), 'Alerts'));
      batch.set(alertRef, {
        roomNumber: normalizeRoom(roomNumber),
        title,
        message,
        priority,
        alertType,
        durationMs,
        broadcastId,
        read: false,
        revoked: false,
        source: 'broadcast',
        timestamp: Date.now(),
        createdAt: serverTimestamp(),
      });
    });

    await batch.commit();
    logFirestoreWrite('Broadcast Alerts Batch', paths.alertsCollection(), {
      broadcastId,
      rooms: chunk.length,
    });
  }
}

async function fetchAllRoomNumbers() {
  const snapshot = await getDocs(collection(db, 'Hotels', getHotelId(), 'Rooms'));
  const rooms = snapshot.docs.map((d) => normalizeRoom(d.id)).filter(Boolean);
  if (rooms.length) return rooms.sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));

  return Array.from({ length: 16 }, (_, i) => String(101 + i));
}

function parseRoomList(value) {
  return [...new Set(String(value || '').split(/[,\s]+/).map(normalizeRoom).filter(Boolean))];
}

function setupPresetButtons() {
  document.querySelectorAll('.alert-preset-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      const preset = ALERT_PRESETS[btn.dataset.preset];
      if (!preset) return;

      document.getElementById('broadcast-title').value = preset.title;
      document.getElementById('broadcast-message').value = preset.message;
      document.getElementById('broadcast-type').value = preset.alertType;
      document.getElementById('broadcast-duration').value = preset.duration;

      document.querySelectorAll('.alert-preset-btn').forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');
      activePreset = btn.dataset.preset;
    });
  });
}

function clearPresetSelection() {
  document.querySelectorAll('.alert-preset-btn').forEach((b) => b.classList.remove('active'));
  activePreset = null;
}

function setupClearActiveButton() {
  document.getElementById('clear-active-broadcast-btn')?.addEventListener('click', async () => {
    const active = broadcastsCache.find((b) => b.status === 'delivered');
    if (!active) {
      toast('No active broadcast to clear', 'error');
      return;
    }
    await revokeBroadcast(active.id, active.title);
  });
}

function listenBroadcastHistory() {
  if (broadcastsUnsub) {
    broadcastsUnsub();
    broadcastsUnsub = null;
  }

  const hotelId = getHotelId();
  if (!hotelId) {
    broadcastsCache = [];
    renderBroadcastHistory([]);
    return;
  }

  const collectionPath = paths.broadcastsCollection();
  logFirestoreListen('Broadcasts', collectionPath);

  const q = query(
    collection(db, 'Hotels', hotelId, 'Broadcasts'),
    orderBy('timestamp', 'desc'),
  );

  broadcastsUnsub = onSnapshot(
    q,
    (snapshot) => {
      hideConnectionError();
      broadcastsCache = snapshot.docs.map((d) => ({ id: d.id, ...d.data() }));
      renderBroadcastHistory(broadcastsCache);
    },
    (err) => {
      console.error('[Firestore ERROR] Broadcasts listener:', err);
      showConnectionError('Could not load broadcast history from Firestore.');
    },
  );
}

function renderBroadcastHistory(broadcasts) {
  const container = document.getElementById('broadcast-history');
  const countBadge = document.getElementById('broadcast-count');
  if (countBadge) countBadge.textContent = broadcasts.length;

  if (!container) return;

  if (!broadcasts.length) {
    container.innerHTML = `<p class="empty-state">No broadcasts sent yet.</p>`;
    return;
  }

  container.innerHTML = broadcasts.map(renderBroadcastRow).join('');

  container.querySelectorAll('[data-action="revoke-broadcast"]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const broadcast = broadcastsCache.find((b) => b.id === btn.dataset.id);
      if (!broadcast || broadcast.status === 'revoked') return;
      if (!confirm(`Revoke "${broadcast.title}" from all TV screens?`)) return;
      btn.disabled = true;
      await revokeBroadcast(broadcast.id, broadcast.title);
      btn.disabled = false;
    });
  });
}

function renderBroadcastRow(broadcast) {
  const isRevoked = broadcast.status === 'revoked';
  const targetLabel =
    broadcast.targetMode === 'all'
      ? `All Rooms (${broadcast.roomCount || '—'})`
      : (broadcast.targetRooms || []).join(', ') || '—';
  const durationLabel = DURATION_LABELS[broadcast.durationMs] || `${broadcast.durationMs / 1000}s`;
  const typeLabel = TYPE_LABELS[broadcast.alertType] || broadcast.alertType || 'Notice';

  return `
    <div class="broadcast-row${isRevoked ? ' broadcast-row-revoked' : ''}" data-searchable data-search-text="${escapeHtml(broadcast.title || '')} ${escapeHtml(broadcast.message || '')}">
      <div class="broadcast-row-top">
        <div class="broadcast-row-meta">
          <span class="broadcast-type-badge broadcast-type-${escapeHtml(broadcast.alertType || 'event')}">${escapeHtml(typeLabel)}</span>
          <span class="broadcast-status-badge ${isRevoked ? 'status-revoked' : 'status-delivered'}">${isRevoked ? 'Revoked' : 'Delivered'}</span>
        </div>
        <span class="broadcast-time">${formatTime(broadcast.timestamp)}</span>
      </div>
      <h4 class="broadcast-title">${escapeHtml(broadcast.title || 'Untitled')}</h4>
      <p class="broadcast-preview">${escapeHtml(broadcast.message || '')}</p>
      <div class="broadcast-row-footer">
        <span class="broadcast-target">📺 ${escapeHtml(targetLabel)} · ${escapeHtml(durationLabel)}</span>
        ${
          !isRevoked
            ? `<button data-action="revoke-broadcast" data-id="${broadcast.id}" class="menu-action-btn danger">Revoke</button>`
            : ''
        }
      </div>
    </div>`;
}

async function revokeBroadcast(broadcastId, title) {
  try {
    await updateDoc(doc(db, 'Hotels', getHotelId(), 'Broadcasts', broadcastId), {
      status: 'revoked',
      revokedAt: serverTimestamp(),
    });
    logFirestoreWrite('Broadcast Revoke', `${paths.broadcastsCollection()}/${broadcastId}`, { status: 'revoked' });

    const alertsQuery = query(
      collection(db, 'Hotels', getHotelId(), 'Alerts'),
      where('broadcastId', '==', broadcastId),
    );
    const snapshot = await getDocs(alertsQuery);

    if (!snapshot.empty) {
      const batch = writeBatch(db);
      snapshot.docs.forEach((alertDoc) => {
        batch.update(alertDoc.ref, { read: true, revoked: true });
      });
      await batch.commit();
      logFirestoreWrite('Broadcast Alerts Revoked', paths.alertsCollection(), {
        broadcastId,
        count: snapshot.size,
      });
    }

    toast(`Revoked "${title}" from TV screens`);
  } catch (err) {
    toast('Failed to revoke broadcast', 'error');
    console.error('[Firestore ERROR] Revoke broadcast failed:', err);
  }
}
