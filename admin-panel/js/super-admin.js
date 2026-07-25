import { db } from './firebase-config.js';
import {
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  deleteDoc,
  updateDoc,
  writeBatch,
  query,
  where,
  onSnapshot,
  serverTimestamp,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import { createHotelAdminAccount, isSuperAdmin } from './auth.js';
import { TenantManager, clearHotelContext, getHotelId } from './tenant-context.js';
import { escapeHtml, toast, openModal, closeModal, setupModalClose } from './utils.js';
import { navigateTo } from './router.js';
import { normalizeHotelId } from './firebase-config.js';

const HOTEL_SUBCOLLECTIONS = ['Rooms', 'Menu', 'Alerts', 'Broadcasts', 'Requests', 'Config'];
const BATCH_LIMIT = 400;

let hotelsUnsub = null;
let hotelsCache = [];
/** @type {{ id: string, name: string, adminUid?: string } | null} */
let pendingDeleteHotel = null;
/** @type {string | null} */
let editingHotelId = null;

export function initSuperAdmin() {
  setupAddHotelModal();
  setupEditHotelModal();
  setupDeleteHotelModal();
  setupImpersonationSelect();
  document.getElementById('add-hotel-btn')?.addEventListener('click', () => {
    openModal('add-hotel-modal');
  });
  document.getElementById('super-admin-refresh')?.addEventListener('click', () => {
    toast('Refreshing hotels…');
  });
}

export function startSuperAdminListeners() {
  stopSuperAdminListeners();
  if (!isSuperAdmin()) return;

  hotelsUnsub = onSnapshot(
    collection(db, 'Hotels'),
    async (snapshot) => {
      hotelsCache = await Promise.all(
        snapshot.docs.map(async (d) => {
          const data = d.data() || {};
          let tvCount = data.activeTvScreens;
          if (typeof tvCount !== 'number') {
            try {
              const roomsSnap = await getDocs(collection(db, 'Hotels', d.id, 'Rooms'));
              tvCount = roomsSnap.docs.filter((r) => {
                const rd = r.data();
                return rd.status === 'occupied' || rd.guestName;
              }).length;
            } catch {
              tvCount = 0;
            }
          }
          return {
            id: d.id,
            name: data.name || d.id,
            adminEmail: data.adminEmail || '',
            adminUid: data.adminUid || '',
            status: data.status || 'active',
            activeTvScreens: tvCount,
            branding: data.branding || {},
            ...data,
          };
        }),
      );
      hotelsCache.sort((a, b) => String(a.name).localeCompare(String(b.name)));
      renderDashboardStats(hotelsCache);
      renderHotelsTable(hotelsCache);
      populateImpersonationSelect(hotelsCache);
    },
    (err) => {
      console.error('[super-admin] hotels listener', err);
      toast('Could not load hotels registry', 'error');
    },
  );
}

export function stopSuperAdminListeners() {
  if (hotelsUnsub) {
    hotelsUnsub();
    hotelsUnsub = null;
  }
}

function renderDashboardStats(hotels) {
  const total = hotels.length;
  const active = hotels.filter((h) => (h.status || 'active') === 'active').length;
  const screens = hotels.reduce((sum, h) => sum + (Number(h.activeTvScreens) || 0), 0);

  const totalEl = document.getElementById('sa-stat-total-hotels');
  const activeEl = document.getElementById('sa-stat-active-hotels');
  const screensEl = document.getElementById('sa-stat-screens');
  if (totalEl) totalEl.textContent = String(total);
  if (activeEl) activeEl.textContent = String(active);
  if (screensEl) screensEl.textContent = String(screens);
}

function brandingOf(hotel) {
  const b = hotel?.branding || {};
  return {
    logoUrl: b.logoUrl || b.logo_url || hotel?.logoUrl || '',
    bgWallpaper: b.bgWallpaper || b.bg_wallpaper || hotel?.bgWallpaper || '',
    themeColor: b.themeColor || b.theme_color || hotel?.themeColor || '#C9A962',
    tagline: b.tagline || hotel?.tagline || '',
    welcomeMessage:
      b.welcomeMessage ||
      b.welcome_message ||
      hotel?.welcome_message ||
      hotel?.welcomeMessage ||
      '',
  };
}

function renderHotelsTable(hotels) {
  const tbody = document.getElementById('hotels-table-body');
  if (!tbody) return;

  if (!hotels.length) {
    tbody.innerHTML = `<tr><td colspan="6" class="empty-state">No hotels onboarded yet. Click “Add New Hotel”.</td></tr>`;
    return;
  }

  tbody.innerHTML = hotels
    .map((h) => {
      const isActive = (h.status || 'active') === 'active';
      const statusClass = isActive ? 'status-pill-active' : 'status-pill-inactive';
      const statusLabel = isActive ? 'Active' : 'Inactive';
      const brand = brandingOf(h);
      return `
      <tr data-searchable data-search-text="${escapeHtml(`${h.name} ${h.id} ${h.adminEmail}`)}">
        <td>
          <div class="sa-hotel-name-cell">
            ${
              brand.logoUrl
                ? `<img src="${escapeHtml(brand.logoUrl)}" alt="" class="sa-hotel-thumb" onerror="this.style.display='none'" />`
                : `<span class="sa-hotel-thumb sa-hotel-thumb-fallback">${escapeHtml((h.name || '?').slice(0, 1).toUpperCase())}</span>`
            }
            <span class="font-semibold">${escapeHtml(h.name)}</span>
          </div>
        </td>
        <td><code class="hotel-id-code">${escapeHtml(h.id)}</code></td>
        <td>${escapeHtml(h.adminEmail || '—')}</td>
        <td class="text-center"><span class="sa-screen-count">${h.activeTvScreens ?? 0}</span></td>
        <td>
          <span class="status-pill ${statusClass}">
            <span class="status-dot" aria-hidden="true"></span>
            ${statusLabel}
          </span>
        </td>
        <td class="text-right">
          <div class="hotel-row-actions">
            <button type="button" class="quick-btn quick-btn-primary" data-open-hotel="${escapeHtml(h.id)}">Open PMS</button>
            <button type="button" class="quick-btn quick-btn-edit" data-edit-hotel="${escapeHtml(h.id)}">Edit</button>
            <button type="button" class="quick-btn quick-btn-danger" data-delete-hotel="${escapeHtml(h.id)}" title="Delete hotel">
              Delete
            </button>
          </div>
        </td>
      </tr>`;
    })
    .join('');

  tbody.querySelectorAll('[data-open-hotel]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const id = btn.getAttribute('data-open-hotel');
      const hotel = hotelsCache.find((h) => h.id === id);
      impersonateHotel(id, hotel);
    });
  });

  tbody.querySelectorAll('[data-edit-hotel]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const id = btn.getAttribute('data-edit-hotel');
      const hotel = hotelsCache.find((h) => h.id === id);
      if (hotel) openEditHotelModal(hotel);
    });
  });

  tbody.querySelectorAll('[data-delete-hotel]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const id = btn.getAttribute('data-delete-hotel');
      const hotel = hotelsCache.find((h) => h.id === id);
      if (hotel) openDeleteHotelModal(hotel);
    });
  });
}

function populateImpersonationSelect(hotels) {
  document.querySelectorAll('[data-impersonate-select]').forEach((select) => {
    const current = select.value || TenantManager.getHotelId();
    select.innerHTML =
      `<option value="">— Select hotel to manage —</option>` +
      hotels
        .map(
          (h) =>
            `<option value="${escapeHtml(h.id)}">${escapeHtml(h.name)} (${escapeHtml(h.id)})</option>`,
        )
        .join('');
    if (current && hotels.some((h) => h.id === current)) {
      select.value = current;
    }
  });
}

function setupImpersonationSelect() {
  document.querySelectorAll('[data-impersonate-select]').forEach((select) => {
    select.addEventListener('change', () => {
      const id = select.value;
      if (!id) return;
      const hotel = hotelsCache.find((h) => h.id === id);
      impersonateHotel(id, hotel);
    });
  });
}

/**
 * Super Admin impersonation entry point.
 * Calls TenantManager.setImpersonatedHotel → all onHotelChange listeners rebind to
 * Hotels/{selectedHotelId}/Rooms|Menu|Alerts|Broadcasts|Requests (+ KDS filter).
 * Inactive hotels stay editable on the master panel but cannot open PMS.
 */
export function impersonateHotel(hotelId, hotelMeta) {
  if (!hotelId) return;
  const status = hotelMeta?.status || 'active';
  if (String(status).toLowerCase() === 'inactive') {
    toast('This hotel is inactive. Reactivate it before opening PMS.', 'error');
    return;
  }
  const meta = {
    name: hotelMeta?.name || hotelId,
    logoUrl: hotelMeta?.branding?.logoUrl || hotelMeta?.logoUrl || '',
    themeColor: hotelMeta?.branding?.themeColor || hotelMeta?.themeColor || '',
    bgWallpaper: hotelMeta?.branding?.bgWallpaper || hotelMeta?.bgWallpaper || '',
    branding: hotelMeta?.branding || {},
    status,
    hotelId,
  };
  TenantManager.setImpersonatedHotel(hotelId, meta);
  toast(`Managing ${meta.name}`);
  navigateTo('/pms');
}

export function getHotelsCache() {
  return hotelsCache;
}

function setupAddHotelModal() {
  setupModalClose('add-hotel-modal', 'add-hotel-close');
  const form = document.getElementById('add-hotel-form');
  form?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = document.getElementById('hotel-name')?.value?.trim();
    let hotelId = normalizeHotelId(document.getElementById('hotel-slug')?.value);
    const adminEmail = document.getElementById('hotel-admin-email')?.value?.trim();
    const password = document.getElementById('hotel-admin-password')?.value || '';
    const logoUrl = document.getElementById('hotel-logo-url')?.value?.trim() || '';
    const themeColor = document.getElementById('hotel-theme-color')?.value?.trim() || '#C9A962';
    const bgWallpaper = document.getElementById('hotel-bg-wallpaper')?.value?.trim() || '';

    if (!name || !hotelId || !adminEmail || password.length < 6) {
      toast('Fill all required fields (password min 6 chars)', 'error');
      return;
    }
    if (!/^[a-z0-9][a-z0-9_]{1,62}$/.test(hotelId)) {
      toast('Hotel ID must be lowercase letters, numbers, underscores (e.g. ikhsana_001)', 'error');
      return;
    }

    const existing = await getDoc(doc(db, 'Hotels', hotelId));
    if (existing.exists()) {
      toast('Hotel ID already exists', 'error');
      return;
    }

    const submitBtn = form.querySelector('button[type="submit"]');
    if (submitBtn) {
      submitBtn.disabled = true;
      submitBtn.textContent = 'Creating…';
    }

    try {
      const adminUid = await createHotelAdminAccount({
        email: adminEmail,
        password,
        hotelId,
        displayName: `${name} Admin`,
      });

      await setDoc(doc(db, 'Hotels', hotelId), {
        name,
        hotelId,
        adminEmail,
        adminUid,
        status: 'active',
        activeTvScreens: 0,
        branding: {
          logoUrl,
          themeColor,
          bgWallpaper,
        },
        createdAt: serverTimestamp(),
      });

      // Seed empty config shell for TV / menu
      await setDoc(
        doc(db, 'Hotels', hotelId, 'Config', 'menuSettings'),
        {
          categories: [
            { key: 'starters', label: 'Starters' },
            { key: 'main_course', label: 'Main Course' },
            { key: 'beverages', label: 'Beverages' },
            { key: 'desserts', label: 'Desserts' },
          ],
          updatedAt: serverTimestamp(),
        },
        { merge: true },
      );

      closeModal('add-hotel-modal');
      form.reset();
      toast(`Hotel “${name}” onboarded`);
    } catch (err) {
      console.error(err);
      toast(err.message || 'Failed to create hotel', 'error');
    } finally {
      if (submitBtn) {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Create Hotel';
      }
    }
  });

  // Auto-slug from name
  document.getElementById('hotel-name')?.addEventListener('input', (e) => {
    const slugInput = document.getElementById('hotel-slug');
    if (!slugInput || slugInput.dataset.touched === '1') return;
    slugInput.value = String(e.target.value || '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '_')
      .replace(/^_|_$/g, '')
      .slice(0, 48);
  });
  document.getElementById('hotel-slug')?.addEventListener('input', (e) => {
    e.target.dataset.touched = '1';
  });
}

function setupEditHotelModal() {
  setupModalClose('edit-hotel-modal', 'edit-hotel-close');
  document.getElementById('edit-hotel-cancel')?.addEventListener('click', () => {
    closeModal('edit-hotel-modal');
    editingHotelId = null;
  });

  const logoInput = document.getElementById('edit-hotel-logo-url');
  const wallpaperInput = document.getElementById('edit-hotel-wallpaper-url');
  const activeToggle = document.getElementById('edit-hotel-active');

  logoInput?.addEventListener('input', () => {
    updateMediaPreview(
      logoInput.value.trim(),
      'edit-hotel-logo-preview',
      'edit-hotel-logo-placeholder',
    );
  });
  wallpaperInput?.addEventListener('input', () => {
    updateMediaPreview(
      wallpaperInput.value.trim(),
      'edit-hotel-wallpaper-preview',
      'edit-hotel-wallpaper-placeholder',
    );
  });
  activeToggle?.addEventListener('change', () => {
    syncActiveToggleLabel();
  });

  document.getElementById('edit-hotel-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const hotelId = editingHotelId || document.getElementById('edit-hotel-id')?.value;
    if (!hotelId) return;

    const name = document.getElementById('edit-hotel-name')?.value?.trim();
    const adminEmail = document.getElementById('edit-hotel-admin-email')?.value?.trim();
    const tagline = document.getElementById('edit-hotel-tagline')?.value?.trim() || '';
    const welcomeMessage =
      document.getElementById('edit-hotel-welcome-message')?.value?.trim() || '';
    const logoUrl = document.getElementById('edit-hotel-logo-url')?.value?.trim() || '';
    const bgWallpaper =
      document.getElementById('edit-hotel-wallpaper-url')?.value?.trim() || '';
    const isActive = Boolean(document.getElementById('edit-hotel-active')?.checked);
    const existing = hotelsCache.find((h) => h.id === hotelId);
    const brand = brandingOf(existing || {});

    if (!name || !adminEmail) {
      toast('Hotel name and admin email are required', 'error');
      return;
    }

    const form = document.getElementById('edit-hotel-form');
    const submitBtn = form?.querySelector('button[type="submit"]');
    if (submitBtn) {
      submitBtn.disabled = true;
      submitBtn.textContent = 'Saving…';
    }

    try {
      await updateDoc(doc(db, 'Hotels', hotelId), {
        name,
        hotel_name: name,
        adminEmail,
        tagline,
        welcome_message: welcomeMessage,
        status: isActive ? 'active' : 'inactive',
        branding: {
          logoUrl,
          themeColor: brand.themeColor || '#C9A962',
          bgWallpaper,
          tagline,
          welcomeMessage,
        },
        logoUrl,
        bgWallpaper,
        updatedAt: serverTimestamp(),
      });
      closeModal('edit-hotel-modal');
      editingHotelId = null;
      toast(`Updated “${name}”`);
      // Table + stats refresh via Hotels onSnapshot
    } catch (err) {
      console.error('[super-admin] edit hotel failed', err);
      toast(err.message || 'Failed to update hotel', 'error');
    } finally {
      if (submitBtn) {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Save Changes';
      }
    }
  });
}

function openEditHotelModal(hotel) {
  editingHotelId = hotel.id;
  const brand = brandingOf(hotel);
  const isActive = (hotel.status || 'active') === 'active';

  const idField = document.getElementById('edit-hotel-id');
  const idLabel = document.getElementById('edit-hotel-id-label');
  if (idField) idField.value = hotel.id;
  if (idLabel) idLabel.textContent = hotel.id;

  const nameEl = document.getElementById('edit-hotel-name');
  const emailEl = document.getElementById('edit-hotel-admin-email');
  const taglineEl = document.getElementById('edit-hotel-tagline');
  const welcomeEl = document.getElementById('edit-hotel-welcome-message');
  const logoEl = document.getElementById('edit-hotel-logo-url');
  const wallEl = document.getElementById('edit-hotel-wallpaper-url');
  const activeEl = document.getElementById('edit-hotel-active');

  if (nameEl) nameEl.value = hotel.name || '';
  if (emailEl) emailEl.value = hotel.adminEmail || '';
  if (taglineEl) taglineEl.value = brand.tagline || '';
  if (welcomeEl) welcomeEl.value = brand.welcomeMessage || '';
  if (logoEl) logoEl.value = brand.logoUrl || '';
  if (wallEl) wallEl.value = brand.bgWallpaper || '';
  if (activeEl) activeEl.checked = isActive;

  updateMediaPreview(
    brand.logoUrl,
    'edit-hotel-logo-preview',
    'edit-hotel-logo-placeholder',
  );
  updateMediaPreview(
    brand.bgWallpaper,
    'edit-hotel-wallpaper-preview',
    'edit-hotel-wallpaper-placeholder',
  );
  syncActiveToggleLabel();
  openModal('edit-hotel-modal');
}

function updateMediaPreview(url, imgId, placeholderId) {
  const img = document.getElementById(imgId);
  const placeholder = document.getElementById(placeholderId);
  if (!img || !placeholder) return;

  if (!url) {
    img.classList.add('hidden');
    img.removeAttribute('src');
    placeholder.classList.remove('hidden');
    return;
  }

  img.onload = () => {
    img.classList.remove('hidden');
    placeholder.classList.add('hidden');
  };
  img.onerror = () => {
    img.classList.add('hidden');
    placeholder.classList.remove('hidden');
    placeholder.textContent = 'Preview unavailable';
  };
  placeholder.textContent = imgId.includes('logo') ? 'Logo preview' : 'Wallpaper preview';
  img.src = url;
}

function syncActiveToggleLabel() {
  const toggle = document.getElementById('edit-hotel-active');
  const label = document.getElementById('edit-hotel-active-label');
  if (!toggle || !label) return;
  label.textContent = toggle.checked ? 'Active' : 'Inactive';
  label.classList.toggle('is-inactive', !toggle.checked);
}

function setupDeleteHotelModal() {
  setupModalClose('delete-hotel-modal', 'delete-hotel-close');
  document.getElementById('delete-hotel-cancel')?.addEventListener('click', () => {
    closeDeleteHotelModal();
  });

  const confirmInput = document.getElementById('delete-hotel-confirm-id');
  const confirmBtn = document.getElementById('delete-hotel-confirm-btn');

  confirmInput?.addEventListener('input', () => {
    syncDeleteConfirmEnabled();
  });

  confirmBtn?.addEventListener('click', async () => {
    if (!pendingDeleteHotel) return;
    const typed = confirmInput?.value?.trim() || '';
    if (typed !== pendingDeleteHotel.id) {
      toast('Hotel ID does not match', 'error');
      return;
    }
    await executeHotelDeletion(pendingDeleteHotel);
  });
}

function openDeleteHotelModal(hotel) {
  pendingDeleteHotel = {
    id: hotel.id,
    name: hotel.name || hotel.id,
    adminUid: hotel.adminUid || '',
  };

  const prompt = document.getElementById('delete-hotel-prompt');
  if (prompt) {
    prompt.textContent =
      `Are you sure you want to delete ${pendingDeleteHotel.name}? ` +
      `This will remove its Firestore data and disassociate all connected TVs.`;
  }

  const expected = document.getElementById('delete-hotel-expected-id');
  if (expected) expected.textContent = pendingDeleteHotel.id;

  const input = document.getElementById('delete-hotel-confirm-id');
  if (input) {
    input.value = '';
    input.placeholder = pendingDeleteHotel.id;
  }

  syncDeleteConfirmEnabled();
  openModal('delete-hotel-modal');
  input?.focus();
}

function closeDeleteHotelModal() {
  pendingDeleteHotel = null;
  const input = document.getElementById('delete-hotel-confirm-id');
  if (input) input.value = '';
  syncDeleteConfirmEnabled();
  closeModal('delete-hotel-modal');
}

function syncDeleteConfirmEnabled() {
  const confirmBtn = document.getElementById('delete-hotel-confirm-btn');
  const typed = document.getElementById('delete-hotel-confirm-id')?.value?.trim() || '';
  const ok = Boolean(pendingDeleteHotel && typed === pendingDeleteHotel.id);
  if (confirmBtn) confirmBtn.disabled = !ok;
}

async function executeHotelDeletion(hotel) {
  const confirmBtn = document.getElementById('delete-hotel-confirm-btn');
  const cancelBtn = document.getElementById('delete-hotel-cancel');
  if (confirmBtn) {
    confirmBtn.disabled = true;
    confirmBtn.textContent = 'Deleting…';
  }
  if (cancelBtn) cancelBtn.disabled = true;

  try {
    await deleteHotelCompletely(hotel.id, hotel.adminUid);
    if (getHotelId() === hotel.id) {
      clearHotelContext();
    }
    closeDeleteHotelModal();
    toast(`Deleted hotel “${hotel.name}”`);
    // onSnapshot on Hotels will refresh the table automatically
  } catch (err) {
    console.error('[super-admin] delete hotel failed', err);
    toast(err.message || 'Failed to delete hotel', 'error');
    syncDeleteConfirmEnabled();
  } finally {
    if (confirmBtn) confirmBtn.textContent = 'Delete permanently';
    if (cancelBtn) cancelBtn.disabled = false;
  }
}

/**
 * Deletes Hotels/{hotelId} + known subcollections, Live_Orders for the tenant,
 * and disassociates users/{uid} linked to this hotel.
 */
async function deleteHotelCompletely(hotelId, adminUid) {
  console.log(`[super-admin] Deleting Hotels/${hotelId} …`);

  for (const sub of HOTEL_SUBCOLLECTIONS) {
    await deleteQueryBatch(collection(db, 'Hotels', hotelId, sub));
  }

  try {
    await deleteQueryBatch(
      query(collection(db, 'Live_Orders'), where('hotelId', '==', hotelId)),
    );
  } catch (err) {
    console.warn('[super-admin] Live_Orders cleanup skipped', err);
  }

  await disassociateHotelUsers(hotelId, adminUid);
  await deleteDoc(doc(db, 'Hotels', hotelId));
  console.log(`[super-admin] Deleted Hotels/${hotelId}`);
}

async function deleteQueryBatch(collectionOrQueryRef) {
  let snap = await getDocs(collectionOrQueryRef);
  while (!snap.empty) {
    let batch = writeBatch(db);
    let ops = 0;
    const commits = [];

    for (const d of snap.docs) {
      batch.delete(d.ref);
      ops += 1;
      if (ops >= BATCH_LIMIT) {
        commits.push(batch.commit());
        batch = writeBatch(db);
        ops = 0;
      }
    }
    if (ops > 0) commits.push(batch.commit());
    await Promise.all(commits);

    snap = await getDocs(collectionOrQueryRef);
  }
}

async function disassociateHotelUsers(hotelId, adminUid) {
  const touched = new Set();

  if (adminUid) {
    try {
      await updateDoc(doc(db, 'users', adminUid), {
        hotelId: '',
        role: 'disabled',
        previousHotelId: hotelId,
        hotelRemovedAt: serverTimestamp(),
      });
      touched.add(adminUid);
    } catch (err) {
      console.warn('[super-admin] adminUid user update failed', adminUid, err);
    }
  }

  try {
    const usersSnap = await getDocs(
      query(collection(db, 'users'), where('hotelId', '==', hotelId)),
    );
    for (const userDoc of usersSnap.docs) {
      if (touched.has(userDoc.id)) continue;
      await updateDoc(userDoc.ref, {
        hotelId: '',
        role: 'disabled',
        previousHotelId: hotelId,
        hotelRemovedAt: serverTimestamp(),
      });
      touched.add(userDoc.id);
    }
  } catch (err) {
    console.warn('[super-admin] users query by hotelId failed', err);
  }

  console.log(`[super-admin] Disassociated ${touched.size} user(s) from ${hotelId}`);
}
