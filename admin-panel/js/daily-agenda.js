/**
 * Corporate Daily Agenda — CRUD for Hotels/{id}/Daily_Agenda/{itemId}
 * Sidebar + module visible only when property_type === 'corporate'.
 *
 * Each agenda row is its own Firestore document so tablet / desktop / TV stay in
 * sync without overwriting the full schedule from a stale local cache.
 */

import { db } from './firebase-config.js';
import {
  collection,
  doc,
  addDoc,
  updateDoc,
  deleteDoc,
  getDoc,
  getDocs,
  onSnapshot,
  serverTimestamp,
  writeBatch,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import { escapeHtml, toast } from './utils.js';
import { logFirestoreWrite, logFirestoreListen } from './paths.js';
import {
  getHotelId,
  onHotelChange,
  onHotelMetaChange,
  isCorporateProperty,
} from './tenant-context.js';

/** @type {{ id: string, time: string, title: string, location: string }[]} */
let agendaItems = [];
/** @type {(() => void) | null} */
let agendaUnsub = null;
let editingId = null;
/** @type {boolean | null} */
let lastCorporateListen = null;
/** @type {Set<string>} */
const migratedHotels = new Set();

export function initDailyAgenda() {
  setupForm();
  applyAgendaChrome();
  onHotelChange(() => {
    editingId = null;
    resetForm();
    applyAgendaChrome();
    listenAgenda();
  });
  onHotelMetaChange(() => {
    applyAgendaChrome();
    const corporate = isCorporateProperty();
    if (corporate !== lastCorporateListen) listenAgenda();
  });
}

/** Show/hide sidebar item for corporate properties only. */
export function applyAgendaChrome() {
  try {
    const navBtn = document.getElementById('nav-agenda');
    const corporate = isCorporateProperty();
    navBtn?.classList.toggle('hidden', !corporate);
  } catch (err) {
    console.error('[agenda] chrome failed', err);
    document.getElementById('nav-agenda')?.classList.add('hidden');
  }
}

function agendaCol(hotelId) {
  return collection(db, 'Hotels', hotelId, 'Daily_Agenda');
}

function agendaDoc(hotelId, id) {
  return doc(db, 'Hotels', hotelId, 'Daily_Agenda', id);
}

function listenAgenda() {
  if (agendaUnsub) {
    agendaUnsub();
    agendaUnsub = null;
  }

  const hotelId = getHotelId();
  const corporate = isCorporateProperty();
  lastCorporateListen = corporate;
  if (!hotelId || !corporate) {
    agendaItems = [];
    renderAgenda();
    return;
  }

  logFirestoreListen('Daily Agenda', `Hotels/${hotelId}/Daily_Agenda`);
  migrateLegacyAgenda(hotelId);

  agendaUnsub = onSnapshot(
    agendaCol(hotelId),
    (snapshot) => {
      agendaItems = snapshot.docs
        .map((d) => ({
          id: d.id,
          time: String(d.data()?.time || '').trim(),
          title: String(d.data()?.title || '').trim(),
          location: String(d.data()?.location || '').trim(),
        }))
        .filter((item) => item.time || item.title || item.location);
      renderAgenda();
    },
    (err) => {
      console.error('[agenda] listener failed', err);
      toast('Could not load daily agenda', 'error');
    },
  );
}

/** One-time copy of Hotels/{id}.daily_agenda[] into the subcollection. */
async function migrateLegacyAgenda(hotelId) {
  if (!hotelId || migratedHotels.has(hotelId)) return;
  migratedHotels.add(hotelId);

  try {
    const existing = await getDocs(agendaCol(hotelId));
    if (!existing.empty) return;

    const hotelSnap = await getDoc(doc(db, 'Hotels', hotelId));
    const raw = hotelSnap.exists() ? hotelSnap.data()?.daily_agenda : null;
    if (!Array.isArray(raw) || !raw.length) return;

    const batch = writeBatch(db);
    raw.forEach((item, index) => {
      const time = String(item?.time || '').trim();
      const title = String(item?.title || item?.name || '').trim();
      const location = String(item?.location || item?.place || item?.venue || '').trim();
      if (!time && !title && !location) return;
      const id = String(item?.id || `agenda_${index}`).trim() || `agenda_${index}`;
      batch.set(agendaDoc(hotelId, id), {
        time,
        title,
        location,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
    });
    await batch.commit();
    logFirestoreWrite('Daily Agenda Migrate', `Hotels/${hotelId}/Daily_Agenda`, {
      count: raw.length,
    });
  } catch (err) {
    migratedHotels.delete(hotelId);
    console.warn('[agenda] legacy migrate skipped', err);
  }
}

function setupForm() {
  const form = document.getElementById('agenda-item-form');
  form?.addEventListener('submit', async (e) => {
    e.preventDefault();
    await saveItem();
  });

  document.getElementById('agenda-cancel-edit-btn')?.addEventListener('click', () => {
    editingId = null;
    resetForm();
  });

  document.getElementById('agenda-clear-all-btn')?.addEventListener('click', () => {
    clearAll();
  });
}

function resetForm() {
  const form = document.getElementById('agenda-item-form');
  const idField = document.getElementById('agenda-item-id');
  const saveBtn = document.getElementById('agenda-save-btn');
  const cancelBtn = document.getElementById('agenda-cancel-edit-btn');
  form?.reset();
  if (idField) idField.value = '';
  if (saveBtn) saveBtn.textContent = 'Add Item';
  if (cancelBtn) cancelBtn.classList.add('hidden');
  editingId = null;
}

function startEdit(item) {
  editingId = item.id;
  const idField = document.getElementById('agenda-item-id');
  const timeEl = document.getElementById('agenda-item-time');
  const titleEl = document.getElementById('agenda-item-title');
  const locationEl = document.getElementById('agenda-item-location');
  const saveBtn = document.getElementById('agenda-save-btn');
  const cancelBtn = document.getElementById('agenda-cancel-edit-btn');
  if (idField) idField.value = item.id;
  if (timeEl) timeEl.value = item.time;
  if (titleEl) titleEl.value = item.title;
  if (locationEl) locationEl.value = item.location;
  if (saveBtn) saveBtn.textContent = 'Save Changes';
  if (cancelBtn) cancelBtn.classList.remove('hidden');
  timeEl?.focus();
}

/**
 * Parse start of a time range string for chronological sorting.
 * Supports "09:00 AM - 10:30 AM", "9:00am", "09:00", etc.
 */
function timeSortKey(timeStr) {
  const raw = String(timeStr || '').trim();
  const match = raw.match(/(\d{1,2}):(\d{2})\s*(AM|PM|am|pm)?/);
  if (!match) return Number.MAX_SAFE_INTEGER;
  let hours = parseInt(match[1], 10);
  const minutes = parseInt(match[2], 10);
  const meridian = (match[3] || '').toUpperCase();
  if (meridian === 'PM' && hours < 12) hours += 12;
  if (meridian === 'AM' && hours === 12) hours = 0;
  return hours * 60 + minutes;
}

function sortedAgenda(items) {
  return [...items].sort((a, b) => {
    const diff = timeSortKey(a.time) - timeSortKey(b.time);
    if (diff !== 0) return diff;
    return String(a.time).localeCompare(String(b.time));
  });
}

async function saveItem() {
  const hotelId = getHotelId();
  if (!hotelId) {
    toast('No property selected', 'error');
    return;
  }

  const time = document.getElementById('agenda-item-time')?.value?.trim() || '';
  const title = document.getElementById('agenda-item-title')?.value?.trim() || '';
  const location = document.getElementById('agenda-item-location')?.value?.trim() || '';
  if (!time || !title || !location) {
    toast('Time, Title, and Location are required', 'error');
    return;
  }

  const saveBtn = document.getElementById('agenda-save-btn');
  const wasEditing = Boolean(editingId);
  if (saveBtn) {
    saveBtn.disabled = true;
    saveBtn.textContent = wasEditing ? 'Saving…' : 'Adding…';
  }

  try {
    const payload = {
      time,
      title,
      location,
      updatedAt: serverTimestamp(),
    };

    if (wasEditing) {
      await updateDoc(agendaDoc(hotelId, editingId), payload);
      logFirestoreWrite('Daily Agenda Update', `Hotels/${hotelId}/Daily_Agenda/${editingId}`, payload);
      toast('Agenda item updated');
    } else {
      const ref = await addDoc(agendaCol(hotelId), {
        ...payload,
        createdAt: serverTimestamp(),
      });
      logFirestoreWrite('Daily Agenda Add', `Hotels/${hotelId}/Daily_Agenda/${ref.id}`, payload);
      toast('Agenda item added');
    }
    resetForm();
  } catch (err) {
    console.error('[agenda] save failed', err);
    toast(err.message || 'Failed to save agenda item', 'error');
  } finally {
    if (saveBtn) {
      saveBtn.disabled = false;
      saveBtn.textContent = editingId ? 'Save Changes' : 'Add Item';
    }
  }
}

async function deleteItem(id) {
  const hotelId = getHotelId();
  if (!hotelId || !id) return;
  const item = agendaItems.find((a) => a.id === id);
  if (!item) return;
  if (!confirm(`Delete “${item.title}”?`)) return;

  try {
    await deleteDoc(agendaDoc(hotelId, id));
    logFirestoreWrite('Daily Agenda Delete', `Hotels/${hotelId}/Daily_Agenda/${id}`, { id });
    if (editingId === id) resetForm();
    toast('Agenda item deleted');
  } catch (err) {
    console.error('[agenda] delete failed', err);
    toast(err.message || 'Failed to delete agenda item', 'error');
  }
}

async function clearAll() {
  const hotelId = getHotelId();
  if (!hotelId) return;
  if (!agendaItems.length) {
    toast('Agenda is already empty');
    return;
  }
  if (!confirm('Clear the entire daily agenda? This cannot be undone.')) return;

  try {
    const snap = await getDocs(agendaCol(hotelId));
    const batch = writeBatch(db);
    snap.docs.forEach((d) => batch.delete(d.ref));
    await batch.commit();
    logFirestoreWrite('Daily Agenda Clear', `Hotels/${hotelId}/Daily_Agenda`, { count: snap.size });
    resetForm();
    toast('Daily agenda cleared');
  } catch (err) {
    console.error('[agenda] clear all failed', err);
    toast(err.message || 'Failed to clear agenda', 'error');
  }
}

function renderAgenda() {
  const list = document.getElementById('agenda-items-list');
  const count = document.getElementById('agenda-item-count');
  const clearBtn = document.getElementById('agenda-clear-all-btn');
  const sorted = sortedAgenda(agendaItems);

  if (count) count.textContent = String(sorted.length);
  if (clearBtn) clearBtn.disabled = sorted.length === 0;
  if (!list) return;

  if (!sorted.length) {
    list.innerHTML = `<p class="empty-state">No agenda items yet. Add today’s schedule above.</p>`;
    return;
  }

  list.innerHTML = sorted
    .map(
      (item) => `
      <article class="agenda-item-card" data-id="${escapeHtml(item.id)}" data-searchable data-search-text="${escapeHtml(`${item.time} ${item.title} ${item.location}`)}">
        <div class="agenda-item-time">${escapeHtml(item.time)}</div>
        <div class="agenda-item-body">
          <h4 class="agenda-item-title">${escapeHtml(item.title)}</h4>
          <p class="agenda-item-location">${escapeHtml(item.location)}</p>
        </div>
        <div class="agenda-item-actions">
          <button type="button" class="quick-btn quick-btn-edit" data-action="edit" data-id="${escapeHtml(item.id)}">Edit</button>
          <button type="button" class="quick-btn quick-btn-danger" data-action="delete" data-id="${escapeHtml(item.id)}">Delete</button>
        </div>
      </article>`,
    )
    .join('');

  list.querySelectorAll('[data-action="edit"]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const item = agendaItems.find((a) => a.id === btn.dataset.id);
      if (item) startEdit(item);
    });
  });
  list.querySelectorAll('[data-action="delete"]').forEach((btn) => {
    btn.addEventListener('click', () => deleteItem(btn.dataset.id));
  });
}
