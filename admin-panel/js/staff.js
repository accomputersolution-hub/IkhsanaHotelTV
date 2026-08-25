/**
 * Staff Management — list / provision operational roles in RTDB staff_users/{uid}.
 * Visible only to admin (via RBAC nav chrome).
 */

import { ref, onValue, update, remove } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-database.js';
import { rtdb } from './firebase-config.js';
import { getHotelId } from './tenant-context.js';
import { toast, escapeHtml, openModal, closeModal, setupModalClose } from './utils.js';
import { canAccessModule } from './rbac.js';
import { STAFF_ROLES, normalizeStaffRole, roleLabel } from './rbac-roles.js';
import { createHotelAdminAccount } from './auth.js';
import { overrideStaffPassword } from './api-client.js';

let staffUnsub = null;
let staffRows = [];
/** @type {{ uid: string, email?: string, displayName?: string } | null} */
let pendingPasswordResetStaff = null;

export function initStaffManagement() {
  setupModalClose('staff-user-modal', 'staff-user-close');
  setupModalClose('staff-override-password-modal', 'staff-override-password-close');

  document.getElementById('add-staff-user-btn')?.addEventListener('click', () => {
    if (!canAccessModule('staff')) {
      toast('Admin access required', 'error');
      return;
    }
    openStaffModal();
  });

  document.getElementById('staff-user-form')?.addEventListener('submit', onSaveStaffUser);
  document
    .getElementById('staff-override-password-form')
    ?.addEventListener('submit', onOverrideStaffPassword);

  startStaffListener();
}

function startStaffListener() {
  if (staffUnsub) {
    staffUnsub();
    staffUnsub = null;
  }
  const hotelId = getHotelId();
  const root = ref(rtdb, 'staff_users');
  staffUnsub = onValue(
    root,
    (snap) => {
      const all = snap.val() || {};
      staffRows = Object.entries(all)
        .map(([uid, data]) => ({
          uid,
          role: normalizeStaffRole(data?.role) || 'admin',
          hotelId: data?.hotelId || data?.hotel_id || '',
          email: data?.email || '',
          displayName: data?.displayName || data?.name || '',
        }))
        .filter((row) => !hotelId || !row.hotelId || row.hotelId === hotelId)
        .sort((a, b) => (a.displayName || a.email).localeCompare(b.displayName || b.email));
      renderStaffTable();
    },
    (err) => {
      console.error('[staff] listen failed', err);
      const tbody = document.getElementById('staff-users-body');
      if (tbody) {
        tbody.innerHTML = `<tr><td colspan="5" class="empty-state">Could not load staff_users</td></tr>`;
      }
    },
  );
}

function renderStaffTable() {
  const tbody = document.getElementById('staff-users-body');
  if (!tbody) return;
  if (!staffRows.length) {
    tbody.innerHTML = `<tr><td colspan="5" class="empty-state">No staff users yet. Add kitchen / reception / housekeeping accounts.</td></tr>`;
    return;
  }
  tbody.innerHTML = staffRows
    .map(
      (row) => `
    <tr data-searchable data-search-text="${escapeHtml(`${row.displayName} ${row.email} ${row.role}`)}">
      <td class="font-semibold">${escapeHtml(row.displayName || '—')}</td>
      <td>${escapeHtml(row.email || '—')}</td>
      <td><span class="staff-role-pill staff-role-${escapeHtml(row.role)}">${escapeHtml(roleLabel(row.role))}</span></td>
      <td><code class="hotel-id-code">${escapeHtml(row.uid.slice(0, 8))}…</code></td>
      <td class="text-right staff-actions-cell">
        <button type="button" class="quick-btn quick-btn-edit" data-edit-staff="${escapeHtml(row.uid)}">Edit role</button>
        <button type="button" class="quick-btn" data-reset-staff-password="${escapeHtml(row.uid)}">Reset password</button>
        <button type="button" class="quick-btn" data-remove-staff="${escapeHtml(row.uid)}">Remove</button>
      </td>
    </tr>`,
    )
    .join('');

  tbody.querySelectorAll('[data-edit-staff]').forEach((btn) => {
    btn.addEventListener('click', () => openStaffModal(btn.getAttribute('data-edit-staff')));
  });
  tbody.querySelectorAll('[data-reset-staff-password]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const uid = btn.getAttribute('data-reset-staff-password');
      const row = staffRows.find((r) => r.uid === uid);
      if (row) openOverridePasswordModal(row);
    });
  });
  tbody.querySelectorAll('[data-remove-staff]').forEach((btn) => {
    btn.addEventListener('click', () => removeStaffUser(btn.getAttribute('data-remove-staff')));
  });
}

function openOverridePasswordModal(row) {
  if (!canAccessModule('staff')) {
    toast('Admin access required', 'error');
    return;
  }
  pendingPasswordResetStaff = row;
  const form = document.getElementById('staff-override-password-form');
  form?.reset();
  const label = document.getElementById('staff-override-password-target');
  if (label) {
    label.textContent = row.displayName || row.email || row.uid;
  }
  const err = document.getElementById('staff-override-password-error');
  if (err) {
    err.textContent = '';
    err.classList.add('hidden');
  }
  openModal('staff-override-password-modal');
}

async function onOverrideStaffPassword(e) {
  e.preventDefault();
  if (!canAccessModule('staff') || !pendingPasswordResetStaff?.uid) {
    toast('Admin access required', 'error');
    return;
  }
  const newPassword = document.getElementById('staff-override-password-new')?.value || '';
  const errEl = document.getElementById('staff-override-password-error');
  const btn = e.target.querySelector('button[type="submit"]');
  const showErr = (msg) => {
    if (!errEl) return;
    errEl.textContent = msg || '';
    errEl.classList.toggle('hidden', !msg);
  };

  showErr('');
  if (newPassword.length < 6) {
    showErr('Password must be at least 6 characters.');
    return;
  }

  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Saving…';
  }
  try {
    await overrideStaffPassword(pendingPasswordResetStaff.uid, newPassword);
    toast('Staff password updated');
    closeModal('staff-override-password-modal');
    e.target.reset();
    pendingPasswordResetStaff = null;
  } catch (err) {
    console.error('[staff] override password failed', err);
    showErr(err.message || 'Failed to update password');
    toast(err.message || 'Failed to update password', 'error');
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.textContent = 'Set Password';
    }
  }
}

function openStaffModal(uid = null) {
  const form = document.getElementById('staff-user-form');
  const title = document.getElementById('staff-user-modal-title');
  form?.reset();
  document.getElementById('staff-edit-uid').value = uid || '';

  const existing = uid ? staffRows.find((r) => r.uid === uid) : null;
  if (existing) {
    if (title) title.textContent = `Edit: ${existing.displayName || existing.email}`;
    document.getElementById('staff-display-name').value = existing.displayName || '';
    document.getElementById('staff-email').value = existing.email || '';
    document.getElementById('staff-email').disabled = true;
    document.getElementById('staff-password-wrap')?.classList.add('hidden');
    document.getElementById('staff-role').value = existing.role;
  } else {
    if (title) title.textContent = 'Add Staff User';
    document.getElementById('staff-email').disabled = false;
    document.getElementById('staff-password-wrap')?.classList.remove('hidden');
  }

  const roleSelect = document.getElementById('staff-role');
  if (roleSelect && !roleSelect.options.length) {
    roleSelect.innerHTML = STAFF_ROLES.map(
      (r) => `<option value="${r}">${roleLabel(r)}</option>`,
    ).join('');
  } else if (roleSelect && existing) {
    roleSelect.value = existing.role;
  }

  openModal('staff-user-modal');
}

async function onSaveStaffUser(e) {
  e.preventDefault();
  if (!canAccessModule('staff')) {
    toast('Admin access required', 'error');
    return;
  }
  const btn = e.target.querySelector('button[type="submit"]');
  btn.disabled = true;
  const uid = document.getElementById('staff-edit-uid').value.trim();
  const displayName = document.getElementById('staff-display-name').value.trim();
  const email = document.getElementById('staff-email').value.trim();
  const password = document.getElementById('staff-password')?.value || '';
  const role = normalizeStaffRole(document.getElementById('staff-role').value) || 'reception';
  const hotelId = getHotelId();

  try {
    if (uid) {
      await update(ref(rtdb, `staff_users/${uid}`), {
        role,
        displayName,
        hotelId,
        updatedAt: Date.now(),
      });
      toast('Staff role updated');
    } else {
      if (!email || password.length < 6) {
        toast('Email + password (min 6) required for new staff', 'error');
        return;
      }
      const newUid = await createHotelAdminAccount({
        email,
        password,
        hotelId,
        displayName,
        staffRole: role,
      });
      toast(`${roleLabel(role)} account created`);
    }
    closeModal('staff-user-modal');
  } catch (err) {
    console.error('[staff] save failed', err);
    toast(err.message || 'Failed to save staff user', 'error');
  } finally {
    btn.disabled = false;
  }
}

async function removeStaffUser(uid) {
  if (!uid || !canAccessModule('staff')) return;
  if (!confirm('Remove this staff RBAC record from Realtime Database? (Auth login may still exist.)')) {
    return;
  }
  try {
    await remove(ref(rtdb, `staff_users/${uid}`));
    toast('Staff RBAC record removed');
  } catch (err) {
    toast(err.message || 'Remove failed', 'error');
  }
}
