/**
 * Corporate Helpdesk Config — CRUD for Hotels/{id}.emergency_contacts
 * Shown in place of Housekeeping Queue when property_type === 'corporate'.
 */

import { db } from './firebase-config.js';
import {
  doc,
  onSnapshot,
  updateDoc,
  serverTimestamp,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import { escapeHtml, toast } from './utils.js';
import { logFirestoreWrite } from './paths.js';
import {
  getHotelId,
  onHotelChange,
  onHotelMetaChange,
  isCorporateProperty,
} from './tenant-context.js';

/** @type {{ id: string, title: string, extension: string }[]} */
let contacts = [];
/** @type {(() => void) | null} */
let contactsUnsub = null;
let editingId = null;

const HK_ICON_HOTEL = `<svg class="nav-svg" viewBox="0 0 24 24" fill="none"><path d="M4 20h16M6 20V9l6-4 6 4v11" stroke="currentColor" stroke-width="1.7"/><path d="M9 13h6M9 16h6" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>`;
const HK_ICON_CORPORATE = `<svg class="nav-svg" viewBox="0 0 24 24" fill="none"><path d="M6.6 3.8h2.6l1.2 3.6-2 1.2a12.5 12.5 0 005.8 5.8l1.2-2 3.6 1.2v2.6A2.2 2.2 0 0117.6 19 14.8 14.8 0 015 6.4a2.2 2.2 0 011.6-2.6z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/></svg>`;

export function initEmergencyContacts() {
  setupForm();
  applyHelpdeskChrome();
  onHotelChange(() => {
    editingId = null;
    resetForm();
    applyHelpdeskChrome();
    listenContacts();
  });
  onHotelMetaChange(() => {
    applyHelpdeskChrome();
  });
}

/** Toggle hotel HK queue vs corporate helpdesk UI + sidebar label/icon. */
export function applyHelpdeskChrome() {
  try {
    const corporate = isCorporateProperty();
    const hotelPanel = document.getElementById('hk-hotel-panel');
    const corpPanel = document.getElementById('hk-corporate-panel');
    const navLabel = document.getElementById('nav-hk-label');
    const navIcon = document.getElementById('nav-hk-icon');
    const navBadge = document.getElementById('nav-hk-badge');
    const moduleTitleEl = document.getElementById('module-title');
    const hkView = document.querySelector('[data-module-view="housekeeping"]');

    hotelPanel?.classList.toggle('hidden', corporate);
    corpPanel?.classList.toggle('hidden', !corporate);

    if (navLabel) {
      navLabel.textContent = corporate ? 'Helpdesk Config' : 'Housekeeping Queue';
    }
    if (navIcon) {
      navIcon.innerHTML = corporate ? HK_ICON_CORPORATE : HK_ICON_HOTEL;
    }
    if (navBadge && corporate) {
      navBadge.classList.add('hidden');
    }

    if (moduleTitleEl && hkView && !hkView.classList.contains('hidden')) {
      moduleTitleEl.textContent = corporate ? 'Helpdesk Config' : 'Housekeeping Queue';
    }
  } catch (err) {
    console.error('[helpdesk] chrome failed', err);
  }
}

function listenContacts() {
  if (contactsUnsub) {
    contactsUnsub();
    contactsUnsub = null;
  }

  const hotelId = getHotelId();
  if (!hotelId || !isCorporateProperty()) {
    contacts = [];
    renderContacts();
    return;
  }

  contactsUnsub = onSnapshot(
    doc(db, 'Hotels', hotelId),
    (snap) => {
      const data = snap.exists() ? snap.data() || {} : {};
      const raw = Array.isArray(data.emergency_contacts) ? data.emergency_contacts : [];
      contacts = raw
        .map((c, index) => ({
          id: String(c?.id || `contact_${index}`),
          title: String(c?.title || '').trim(),
          extension: String(c?.extension || '').trim(),
        }))
        .filter((c) => c.title || c.extension);
      renderContacts();
    },
    (err) => {
      console.error('[helpdesk] contacts listener failed', err);
      toast('Could not load emergency contacts', 'error');
    },
  );
}

function setupForm() {
  const form = document.getElementById('helpdesk-contact-form');
  form?.addEventListener('submit', async (e) => {
    e.preventDefault();
    await saveContact();
  });

  document.getElementById('helpdesk-cancel-edit-btn')?.addEventListener('click', () => {
    editingId = null;
    resetForm();
  });
}

function resetForm() {
  const form = document.getElementById('helpdesk-contact-form');
  const idField = document.getElementById('helpdesk-contact-id');
  const saveBtn = document.getElementById('helpdesk-save-btn');
  const cancelBtn = document.getElementById('helpdesk-cancel-edit-btn');
  form?.reset();
  if (idField) idField.value = '';
  if (saveBtn) saveBtn.textContent = 'Add Contact';
  if (cancelBtn) cancelBtn.classList.add('hidden');
  editingId = null;
}

function startEdit(contact) {
  editingId = contact.id;
  const idField = document.getElementById('helpdesk-contact-id');
  const titleEl = document.getElementById('helpdesk-contact-title');
  const extEl = document.getElementById('helpdesk-contact-extension');
  const saveBtn = document.getElementById('helpdesk-save-btn');
  const cancelBtn = document.getElementById('helpdesk-cancel-edit-btn');
  if (idField) idField.value = contact.id;
  if (titleEl) titleEl.value = contact.title;
  if (extEl) extEl.value = contact.extension;
  if (saveBtn) saveBtn.textContent = 'Save Changes';
  if (cancelBtn) cancelBtn.classList.remove('hidden');
  titleEl?.focus();
}

function newContactId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `c_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

async function saveContact() {
  const hotelId = getHotelId();
  if (!hotelId) {
    toast('No property selected', 'error');
    return;
  }

  const title = document.getElementById('helpdesk-contact-title')?.value?.trim() || '';
  const extension = document.getElementById('helpdesk-contact-extension')?.value?.trim() || '';
  if (!title || !extension) {
    toast('Title and Extension are required', 'error');
    return;
  }

  const saveBtn = document.getElementById('helpdesk-save-btn');
  if (saveBtn) {
    saveBtn.disabled = true;
    saveBtn.textContent = editingId ? 'Saving…' : 'Adding…';
  }

  try {
    let next;
    if (editingId) {
      next = contacts.map((c) =>
        c.id === editingId ? { id: c.id, title, extension } : c,
      );
    } else {
      next = [...contacts, { id: newContactId(), title, extension }];
    }

    await persistContacts(hotelId, next);
    toast(editingId ? 'Contact updated' : 'Contact added');
    resetForm();
  } catch (err) {
    console.error('[helpdesk] save failed', err);
    toast(err.message || 'Failed to save contact', 'error');
  } finally {
    if (saveBtn) {
      saveBtn.disabled = false;
      saveBtn.textContent = editingId ? 'Save Changes' : 'Add Contact';
    }
  }
}

async function deleteContact(id) {
  const hotelId = getHotelId();
  if (!hotelId) return;
  const contact = contacts.find((c) => c.id === id);
  if (!contact) return;
  if (!confirm(`Delete “${contact.title}”?`)) return;

  try {
    const next = contacts.filter((c) => c.id !== id);
    await persistContacts(hotelId, next);
    if (editingId === id) resetForm();
    toast('Contact deleted');
  } catch (err) {
    console.error('[helpdesk] delete failed', err);
    toast(err.message || 'Failed to delete contact', 'error');
  }
}

async function persistContacts(hotelId, nextContacts) {
  const payload = {
    emergency_contacts: nextContacts.map((c) => ({
      id: c.id,
      title: c.title,
      extension: c.extension,
    })),
    updatedAt: serverTimestamp(),
  };
  await updateDoc(doc(db, 'Hotels', hotelId), payload);
  logFirestoreWrite('Emergency Contacts', `Hotels/${hotelId}`, {
    emergency_contacts: payload.emergency_contacts,
  });
  // Optimistic local update; snapshot will reconcile.
  contacts = nextContacts;
  renderContacts();
}

function renderContacts() {
  const list = document.getElementById('helpdesk-contacts-list');
  const count = document.getElementById('helpdesk-contact-count');
  if (count) count.textContent = String(contacts.length);
  if (!list) return;

  if (!contacts.length) {
    list.innerHTML = `<p class="empty-state">No emergency contacts yet. Add one above.</p>`;
    return;
  }

  list.innerHTML = contacts
    .map(
      (c) => `
      <article class="helpdesk-contact-card" data-id="${escapeHtml(c.id)}">
        <div class="helpdesk-contact-body">
          <h4 class="helpdesk-contact-title">${escapeHtml(c.title)}</h4>
          <p class="helpdesk-contact-ext">${escapeHtml(c.extension)}</p>
        </div>
        <div class="helpdesk-contact-actions">
          <button type="button" class="quick-btn quick-btn-edit" data-action="edit" data-id="${escapeHtml(c.id)}">Edit</button>
          <button type="button" class="quick-btn quick-btn-danger" data-action="delete" data-id="${escapeHtml(c.id)}">Delete</button>
        </div>
      </article>`,
    )
    .join('');

  list.querySelectorAll('[data-action="edit"]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const contact = contacts.find((c) => c.id === btn.dataset.id);
      if (contact) startEdit(contact);
    });
  });
  list.querySelectorAll('[data-action="delete"]').forEach((btn) => {
    btn.addEventListener('click', () => deleteContact(btn.dataset.id));
  });
}
