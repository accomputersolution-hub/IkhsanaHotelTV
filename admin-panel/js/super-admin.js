import { db, rtdb } from './firebase-config.js';
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
import { ref as rtdbRef, set as rtdbSet } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-database.js';
import { createHotelAdminAccount, isSuperAdmin } from './auth.js';
import { TenantManager, clearHotelContext, getHotelId } from './tenant-context.js';
import { escapeHtml, toast, openModal, closeModal, setupModalClose } from './utils.js';
import { navigateTo } from './router.js';
import { normalizeHotelId } from './firebase-config.js';
import { normalizeRoom, formatRoomLabel, isNumericRoomId } from './paths.js';
import {
  SUPER_ADMIN_ROOM_MANAGER,
  isSuperAdminManagedRoom,
  purgeUnmanagedRooms,
} from './room-inventory.js';

const HOTEL_SUBCOLLECTIONS = [
  'Rooms',
  'Menu',
  'Alerts',
  'Broadcasts',
  'Requests',
  'Config',
  'Emergency_Contacts',
  'Daily_Agenda',
];
const BATCH_LIMIT = 400;
/** First sequential room id when generating rooms for a new property. */
const ROOM_START_NUMBER = 101;

let hotelsUnsub = null;
let hotelsCache = [];
/** @type {{ id: string, name: string, adminUid?: string } | null} */
let pendingDeleteHotel = null;
/** @type {string | null} */
let editingHotelId = null;
/** @type {string | null} */
let kioskHotelId = null;
/** @type {Array<{ id: string, [key: string]: any }>} */
let editingHotelRooms = [];
let editingRoomsBusy = false;

export function initSuperAdmin() {
  setupAddHotelModal();
  setupEditHotelModal();
  setupDeleteHotelModal();
  setupKioskSettingsModal();
  setupImpersonationSelect();
  document.getElementById('add-hotel-btn')?.addEventListener('click', () => {
    resetCreateRoomBlocks();
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
          if (typeof tvCount !== 'number' || !Number.isFinite(tvCount)) {
            // Prefer explicit counter written on pair/unpair — do not invent from guest status.
            tvCount = Number(data.active_tv_screens);
          }
          if (!Number.isFinite(tvCount) || tvCount < 0) tvCount = 0;
          return {
            id: d.id,
            name: data.name || d.id,
            adminEmail: data.adminEmail || '',
            adminUid: data.adminUid || '',
            status: data.status || 'active',
            activeTvScreens: Math.floor(tvCount),
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
    bgWallpaperDark:
      b.bgWallpaperDark ||
      b.bg_wallpaper_dark ||
      b.bgWallpaperNight ||
      b.bg_wallpaper_night ||
      hotel?.bgWallpaperDark ||
      hotel?.bg_wallpaper_dark ||
      hotel?.bgWallpaperNight ||
      '',
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

/** Encode spaces so Android Coil can fetch ImgBB / CDN paths. */
function sanitizeImageUrl(raw) {
  const trimmed = String(raw || '').trim();
  if (!trimmed) return '';
  try {
    const url = new URL(trimmed);
    url.pathname = url.pathname
      .split('/')
      .map((segment) => {
        try {
          return encodeURIComponent(decodeURIComponent(segment));
        } catch {
          return encodeURIComponent(segment);
        }
      })
      .join('/');
    return url.toString();
  } catch {
    return trimmed.replace(/ /g, '%20');
  }
}

/** ImgBB viewer pages (`ibb.co/…`) are HTML — TV needs `i.ibb.co/…` image bytes. */
function assertDirectImageUrl(url, label) {
  if (!url) return true;
  try {
    const host = new URL(url).hostname.toLowerCase();
    if (host === 'ibb.co' || host === 'www.ibb.co') {
      toast(
        `${label}: ImgBB page link TV pe nahi chalta. Image pe right-click → Copy image address (i.ibb.co)`,
        'error',
      );
      return false;
    }
  } catch {
    toast(`${label}: valid https image link lagao`, 'error');
    return false;
  }
  return true;
}

function normalizePropertyType(value) {
  return String(value || 'hotel').trim().toLowerCase() === 'corporate'
    ? 'corporate'
    : 'hotel';
}

function propertyTypeLabel(value) {
  return normalizePropertyType(value) === 'corporate' ? 'Corporate' : 'Hotel';
}

function renderHotelsTable(hotels) {
  const tbody = document.getElementById('hotels-table-body');
  if (!tbody) return;

  if (!hotels.length) {
    tbody.innerHTML = `<tr><td colspan="7" class="empty-state">No hotels onboarded yet. Click “Add New Hotel”.</td></tr>`;
    return;
  }

  tbody.innerHTML = hotels
    .map((h) => {
      const isActive = (h.status || 'active') === 'active';
      const statusClass = isActive ? 'status-pill-active' : 'status-pill-inactive';
      const statusLabel = isActive ? 'Active' : 'Inactive';
      const brand = brandingOf(h);
      const propertyType = normalizePropertyType(h.property_type || h.propertyType);
      const typeClass =
        propertyType === 'corporate' ? 'property-type-badge-corporate' : 'property-type-badge-hotel';
      return `
      <tr data-searchable data-hotel-id="${escapeHtml(h.id)}" data-search-text="${escapeHtml(`${h.name} ${h.id} ${h.adminEmail} ${propertyType}`)}">
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
        <td><span class="property-type-badge ${typeClass}">${escapeHtml(propertyTypeLabel(propertyType))}</span></td>
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
            <button type="button" class="btn-secondary kiosk-settings-btn" data-id="${escapeHtml(h.id)}">
              Kiosk Settings
            </button>
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

  tbody.querySelectorAll('.kiosk-settings-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      const hotelId =
        btn.getAttribute('data-id') ||
        btn.closest('tr')?.getAttribute('data-hotel-id');
      console.log('Opening Kiosk Settings for Hotel ID:', hotelId);
      if (!hotelId) {
        toast('Hotel ID not found', 'error');
        return;
      }
      window.currentKioskHotelId = hotelId;
      openKioskSettingsModal(hotelId);
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
    property_type: hotelMeta?.property_type || hotelMeta?.propertyType || 'hotel',
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
  document.getElementById('hotel-add-room-block-btn')?.addEventListener('click', () => {
    appendRoomBlockCard('hotel-room-blocks');
    updateRoomBlocksSummary('hotel-room-blocks', 'hotel-room-blocks-summary');
  });
  document.getElementById('hotel-room-blocks')?.addEventListener('click', (e) => {
    const btn = e.target instanceof Element ? e.target.closest('[data-remove-block]') : null;
    if (!(btn instanceof HTMLElement)) return;
    const card = btn.closest('.room-block-card');
    card?.remove();
    renumberRoomBlockCards('hotel-room-blocks');
    updateRoomBlocksSummary('hotel-room-blocks', 'hotel-room-blocks-summary');
  });
  document.getElementById('hotel-room-blocks')?.addEventListener('change', (e) => {
    const select = e.target;
    if (!(select instanceof HTMLSelectElement) || !select.classList.contains('room-block-gen-type')) {
      updateRoomBlocksSummary('hotel-room-blocks', 'hotel-room-blocks-summary');
      return;
    }
    const card = select.closest('.room-block-card');
    syncRoomBlockModeUi(card);
    updateRoomBlocksSummary('hotel-room-blocks', 'hotel-room-blocks-summary');
  });
  document.getElementById('hotel-room-blocks')?.addEventListener('input', () => {
    updateRoomBlocksSummary('hotel-room-blocks', 'hotel-room-blocks-summary');
  });
  resetCreateRoomBlocks();

  const form = document.getElementById('add-hotel-form');
  form?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = document.getElementById('hotel-name')?.value?.trim();
    let hotelId = normalizeHotelId(document.getElementById('hotel-slug')?.value);
    let publicSlug = String(document.getElementById('hotel-public-slug')?.value || '')
      .trim()
      .toLowerCase()
      .replace(/-/g, '_')
      .replace(/[^a-z0-9_]/g, '')
      .slice(0, 48);
    const adminEmail = document.getElementById('hotel-admin-email')?.value?.trim();
    const password = document.getElementById('hotel-admin-password')?.value || '';
    const logoUrl = sanitizeImageUrl(document.getElementById('hotel-logo-url')?.value || '');
    const themeColor = document.getElementById('hotel-theme-color')?.value?.trim() || '#C9A962';
    const bgWallpaper = sanitizeImageUrl(document.getElementById('hotel-bg-wallpaper')?.value || '');
    const bgWallpaperDark = sanitizeImageUrl(
      document.getElementById('hotel-bg-wallpaper-dark')?.value || '',
    );
    const propertyType = normalizePropertyType(
      document.getElementById('hotel-property-type')?.value,
    );
    const roomPlan = expandRoomBlocks(collectRoomBlocks('hotel-room-blocks'));
    if (roomPlan.error) {
      toast(roomPlan.error, 'error');
      return;
    }
    if (!roomPlan.rooms.length) {
      toast('Add at least one room block with rooms to create', 'error');
      return;
    }
    if (roomPlan.rooms.length > 500) {
      toast('Too many rooms in one create (max 500). Split across properties or add later.', 'error');
      return;
    }
    const totalRooms = roomPlan.rooms.length;

    if (!name || !hotelId || !publicSlug || !adminEmail || password.length < 6) {
      toast('Fill all required fields (password min 6 chars)', 'error');
      return;
    }
    if (!/^[a-z0-9][a-z0-9_]{1,62}$/.test(hotelId)) {
      toast('Internal Hotel ID must be lowercase letters, numbers, underscores (e.g. hotel_001)', 'error');
      return;
    }
    if (!/^[a-z0-9][a-z0-9_]{1,47}$/.test(publicSlug)) {
      toast('Public slug must be lowercase (e.g. grand_hotel) — used as subdomain', 'error');
      return;
    }
    if (
      !assertDirectImageUrl(logoUrl, 'Logo') ||
      !assertDirectImageUrl(bgWallpaper, 'Wallpaper') ||
      !assertDirectImageUrl(bgWallpaperDark, 'Night wallpaper')
    ) {
      return;
    }

    const existing = await getDoc(doc(db, 'Hotels', hotelId));
    if (existing.exists()) {
      toast('Internal Hotel ID already exists', 'error');
      return;
    }
    const publicExisting = await getDoc(doc(db, 'public_hotels', publicSlug));
    if (publicExisting.exists()) {
      toast('Public slug already taken — choose another subdomain', 'error');
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
        public_slug: publicSlug,
        adminEmail,
        adminUid,
        property_type: propertyType,
        status: 'active',
        activeTvScreens: 0,
        totalRooms,
        isKioskModeEnabled: true,
        allowOverlayPopups: true,
        allow_overlay_popups: true,
        allowedPackages: [
          'com.google.android.youtube.tv',
          'com.amazon.amazonvideo.livingroom',
        ],
        branding: {
          logoUrl,
          logo_url: logoUrl,
          themeColor,
          bgWallpaper,
          bg_wallpaper: bgWallpaper,
          bgWallpaperDark,
          bg_wallpaper_dark: bgWallpaperDark,
        },
        createdAt: serverTimestamp(),
      });

      // Public directory — anonymous kiosk may read ONLY this doc (not Hotels/{id})
      await setDoc(doc(db, 'public_hotels', publicSlug), {
        hotelId,
        name,
        logoUrl,
        themeColor,
        bgWallpaper,
        bgWallpaperDark,
        bg_wallpaper_dark: bgWallpaperDark,
        property_type: propertyType,
        status: 'active',
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
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

      // Dynamic rooms from category blocks → flat Hotels/{id}/Rooms/{roomId}
      await batchCreateRooms(hotelId, roomPlan.rooms);

      closeModal('add-hotel-modal');
      form.reset();
      resetCreateRoomBlocks();
      toast(`Hotel “${name}” onboarded with ${totalRooms} rooms`);
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
    const raw = String(e.target.value || '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '_')
      .replace(/^_|_$/g, '')
      .slice(0, 48);
    const slugInput = document.getElementById('hotel-slug');
    const publicInput = document.getElementById('hotel-public-slug');
    if (slugInput && slugInput.dataset.touched !== '1') {
      slugInput.value = raw ? `${raw}_001`.slice(0, 48) : '';
    }
    if (publicInput && publicInput.dataset.touched !== '1') {
      // Public subdomain: shorter marketing slug (no _001 suffix)
      publicInput.value = raw.split('_').filter(Boolean).slice(0, 2).join('_').slice(0, 32);
    }
  });
  document.getElementById('hotel-slug')?.addEventListener('input', (e) => {
    e.target.dataset.touched = '1';
  });
  document.getElementById('hotel-public-slug')?.addEventListener('input', (e) => {
    e.target.dataset.touched = '1';
  });
}

function setupEditHotelModal() {
  setupModalClose('edit-hotel-modal', 'edit-hotel-close');
  document.getElementById('edit-hotel-cancel')?.addEventListener('click', () => {
    closeModal('edit-hotel-modal');
    editingHotelId = null;
    editingHotelRooms = [];
    hideEditAddBlockPanel();
  });

  document.getElementById('edit-hotel-add-rooms-btn')?.addEventListener('click', () => {
    showEditAddBlockPanel();
  });
  document.getElementById('edit-hotel-add-block-cancel')?.addEventListener('click', () => {
    hideEditAddBlockPanel();
  });
  document.getElementById('edit-hotel-add-block-submit')?.addEventListener('click', () => {
    void onGenerateEditRoomBlock();
  });
  document.getElementById('edit-hotel-room-blocks')?.addEventListener('click', (e) => {
    const btn = e.target instanceof Element ? e.target.closest('[data-remove-block]') : null;
    if (!(btn instanceof HTMLElement)) return;
    const card = btn.closest('.room-block-card');
    const list = document.getElementById('edit-hotel-room-blocks');
    if (list && list.querySelectorAll('.room-block-card').length <= 1) {
      toast('Keep at least one block, or Cancel', 'error');
      return;
    }
    card?.remove();
    renumberRoomBlockCards('edit-hotel-room-blocks');
  });
  document.getElementById('edit-hotel-room-blocks')?.addEventListener('change', (e) => {
    const select = e.target;
    if (!(select instanceof HTMLSelectElement) || !select.classList.contains('room-block-gen-type')) {
      return;
    }
    syncRoomBlockModeUi(select.closest('.room-block-card'));
  });

  document.getElementById('edit-hotel-rooms-list')?.addEventListener('click', (e) => {
    const btn = e.target instanceof Element ? e.target.closest('[data-room-action]') : null;
    if (!(btn instanceof HTMLElement)) return;
    const action = btn.dataset.roomAction;
    const roomId = btn.dataset.roomId;
    if (!action || !roomId || !editingHotelId) return;
    if (action === 'rename') void onRenameRoomClick(roomId);
    if (action === 'delete') void onDeleteRoomClick(roomId);
  });

  const logoInput = document.getElementById('edit-hotel-logo-url');
  const wallpaperInput = document.getElementById('edit-hotel-wallpaper-url');
  const wallpaperDarkInput = document.getElementById('edit-hotel-wallpaper-dark-url');
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
  wallpaperDarkInput?.addEventListener('input', () => {
    updateMediaPreview(
      wallpaperDarkInput.value.trim(),
      'edit-hotel-wallpaper-dark-preview',
      'edit-hotel-wallpaper-dark-placeholder',
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
    const logoUrl = sanitizeImageUrl(
      document.getElementById('edit-hotel-logo-url')?.value || '',
    );
    const bgWallpaper = sanitizeImageUrl(
      document.getElementById('edit-hotel-wallpaper-url')?.value || '',
    );
    const bgWallpaperDark = sanitizeImageUrl(
      document.getElementById('edit-hotel-wallpaper-dark-url')?.value || '',
    );
    const isActive = Boolean(document.getElementById('edit-hotel-active')?.checked);
    const propertyType = normalizePropertyType(
      document.getElementById('edit-hotel-property-type')?.value,
    );
    const existing = hotelsCache.find((h) => h.id === hotelId);
    const brand = brandingOf(existing || {});

    if (!name || !adminEmail) {
      toast('Hotel name and admin email are required', 'error');
      return;
    }
    if (
      !assertDirectImageUrl(logoUrl, 'Logo') ||
      !assertDirectImageUrl(bgWallpaper, 'Wallpaper') ||
      !assertDirectImageUrl(bgWallpaperDark, 'Night wallpaper')
    ) {
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
        property_type: propertyType,
        status: isActive ? 'active' : 'inactive',
        totalRooms: editingHotelRooms.length,
        branding: {
          logoUrl,
          logo_url: logoUrl,
          themeColor: brand.themeColor || '#C9A962',
          bgWallpaper,
          bg_wallpaper: bgWallpaper,
          bgWallpaperDark,
          bg_wallpaper_dark: bgWallpaperDark,
          tagline,
          welcomeMessage,
        },
        logoUrl,
        logo_url: logoUrl,
        bgWallpaper,
        bg_wallpaper: bgWallpaper,
        bgWallpaperDark,
        bg_wallpaper_dark: bgWallpaperDark,
        updatedAt: serverTimestamp(),
      });
      closeModal('edit-hotel-modal');
      editingHotelId = null;
      editingHotelRooms = [];
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
  const propertyTypeEl = document.getElementById('edit-hotel-property-type');
  const taglineEl = document.getElementById('edit-hotel-tagline');
  const welcomeEl = document.getElementById('edit-hotel-welcome-message');
  const logoEl = document.getElementById('edit-hotel-logo-url');
  const wallEl = document.getElementById('edit-hotel-wallpaper-url');
  const wallDarkEl = document.getElementById('edit-hotel-wallpaper-dark-url');
  const activeEl = document.getElementById('edit-hotel-active');

  if (nameEl) nameEl.value = hotel.name || '';
  if (emailEl) emailEl.value = hotel.adminEmail || '';
  if (propertyTypeEl) {
    propertyTypeEl.value = normalizePropertyType(hotel.property_type || hotel.propertyType);
  }
  if (taglineEl) taglineEl.value = brand.tagline || '';
  if (welcomeEl) welcomeEl.value = brand.welcomeMessage || '';
  if (logoEl) logoEl.value = brand.logoUrl || '';
  if (wallEl) wallEl.value = brand.bgWallpaper || '';
  if (wallDarkEl) wallDarkEl.value = brand.bgWallpaperDark || '';
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
  updateMediaPreview(
    brand.bgWallpaperDark,
    'edit-hotel-wallpaper-dark-preview',
    'edit-hotel-wallpaper-dark-placeholder',
  );
  syncActiveToggleLabel();
  editingHotelRooms = [];
  hideEditAddBlockPanel();
  renderEditingHotelRooms();
  openModal('edit-hotel-modal');
  void loadEditingHotelRooms(hotel.id);
}

const UNCATEGORIZED_ROOM_CATEGORY = 'Uncategorized';

/**
 * Vacant room shell written when Super Admin generates rooms.
 * Flat path: Hotels/{hotelId}/Rooms/{roomId} + category string.
 * @param {string} roomId
 * @param {string} [category]
 */
function vacantRoomPayload(roomId, category = '') {
  const id = normalizeRoom(roomId);
  const cat = String(category || '').trim() || UNCATEGORIZED_ROOM_CATEGORY;
  return {
    roomNumber: id,
    category: cat,
    managedBy: SUPER_ADMIN_ROOM_MANAGER,
    roomType: 'deluxe',
    status: 'vacant',
    guestName: 'Guest',
    guestPhone: '',
    checkOutDate: '',
    hotelName: '',
    occupied: false,
    cleaned: true,
    updatedAt: serverTimestamp(),
  };
}

function isValidRoomDocId(raw) {
  const id = normalizeRoom(raw);
  if (!id || id.length > 64) return false;
  if (id.includes('/') || id.includes('..')) return false;
  return true;
}

function roomBlockCardHtml(index, defaults = {}) {
  const category = escapeHtml(defaults.category || '');
  const mode = defaults.mode === 'custom' ? 'custom' : 'numeric';
  const start = defaults.start ?? (ROOM_START_NUMBER + index * 100);
  const end = defaults.end ?? start + 9;
  const names = escapeHtml(defaults.names || '');
  return `
    <div class="room-block-card" data-block-index="${index}">
      <div class="room-block-card-head">
        <span class="room-block-card-title">Block ${index + 1}</span>
        <button type="button" class="quick-btn" data-remove-block aria-label="Remove block">&times;</button>
      </div>
      <div class="room-block-fields">
        <div>
          <label>Category Name</label>
          <input type="text" class="room-block-category" required maxlength="80"
            placeholder="e.g. First Floor / Suite Rooms" value="${category}" />
        </div>
        <div class="room-block-grid">
          <div>
            <label>Generation Type</label>
            <select class="room-block-gen-type">
              <option value="numeric"${mode === 'numeric' ? ' selected' : ''}>Numeric Range</option>
              <option value="custom"${mode === 'custom' ? ' selected' : ''}>Custom Names</option>
            </select>
          </div>
        </div>
        <div class="room-block-numeric${mode === 'custom' ? ' hidden' : ''}">
          <div class="room-block-grid">
            <div>
              <label>Start No.</label>
              <input type="number" class="room-block-start" min="1" max="9999" step="1" value="${start}" />
            </div>
            <div>
              <label>End No.</label>
              <input type="number" class="room-block-end" min="1" max="9999" step="1" value="${end}" />
            </div>
          </div>
        </div>
        <div class="room-block-custom${mode === 'custom' ? '' : ' hidden'}">
          <label>Custom Names (comma-separated)</label>
          <textarea class="room-block-names" placeholder="Middle East, Mandela, Board Room A">${names}</textarea>
        </div>
      </div>
    </div>`;
}

function syncRoomBlockModeUi(card) {
  if (!card) return;
  const select = card.querySelector('.room-block-gen-type');
  const mode = select instanceof HTMLSelectElement ? select.value : 'numeric';
  card.querySelector('.room-block-numeric')?.classList.toggle('hidden', mode !== 'numeric');
  card.querySelector('.room-block-custom')?.classList.toggle('hidden', mode !== 'custom');
}

function appendRoomBlockCard(containerId, defaults = {}) {
  const container = document.getElementById(containerId);
  if (!container) return;
  const index = container.querySelectorAll('.room-block-card').length;
  container.insertAdjacentHTML('beforeend', roomBlockCardHtml(index, defaults));
  const card = container.lastElementChild;
  syncRoomBlockModeUi(card);
}

function renumberRoomBlockCards(containerId) {
  const container = document.getElementById(containerId);
  if (!container) return;
  container.querySelectorAll('.room-block-card').forEach((card, index) => {
    card.dataset.blockIndex = String(index);
    const title = card.querySelector('.room-block-card-title');
    if (title) title.textContent = `Block ${index + 1}`;
  });
}

function resetCreateRoomBlocks() {
  const container = document.getElementById('hotel-room-blocks');
  if (!container) return;
  container.innerHTML = '';
  appendRoomBlockCard('hotel-room-blocks', {
    category: 'First Floor',
    mode: 'numeric',
    start: 101,
    end: 110,
  });
  updateRoomBlocksSummary('hotel-room-blocks', 'hotel-room-blocks-summary');
}

function showEditAddBlockPanel() {
  const panel = document.getElementById('edit-hotel-add-block-panel');
  const container = document.getElementById('edit-hotel-room-blocks');
  if (!panel || !container) return;
  container.innerHTML = '';
  const nextStart = nextSequentialRoomNumber(editingHotelRooms.map((r) => String(r.id)));
  appendRoomBlockCard('edit-hotel-room-blocks', {
    category: '',
    mode: 'numeric',
    start: nextStart,
    end: nextStart + 4,
  });
  panel.classList.remove('hidden');
}

function hideEditAddBlockPanel() {
  document.getElementById('edit-hotel-add-block-panel')?.classList.add('hidden');
  const container = document.getElementById('edit-hotel-room-blocks');
  if (container) container.innerHTML = '';
}

/**
 * @returns {{ category: string, mode: 'numeric'|'custom', start?: number, end?: number, names?: string[] }[]}
 */
function collectRoomBlocks(containerId) {
  const container = document.getElementById(containerId);
  if (!container) return [];
  return [...container.querySelectorAll('.room-block-card')].map((card) => {
    const category = String(card.querySelector('.room-block-category')?.value || '').trim();
    const mode =
      card.querySelector('.room-block-gen-type')?.value === 'custom' ? 'custom' : 'numeric';
    const start = Number.parseInt(String(card.querySelector('.room-block-start')?.value || ''), 10);
    const end = Number.parseInt(String(card.querySelector('.room-block-end')?.value || ''), 10);
    const namesRaw = String(card.querySelector('.room-block-names')?.value || '');
    const names = namesRaw
      .split(',')
      .map((n) => normalizeRoom(n))
      .filter(Boolean);
    return { category, mode, start, end, names };
  });
}

/**
 * Expand blocks → flat room list with categories. Detects duplicate ids.
 * @returns {{ rooms: Array<{ roomId: string, category: string }>, error?: string }}
 */
function expandRoomBlocks(blocks) {
  /** @type {Array<{ roomId: string, category: string }>} */
  const rooms = [];
  const seen = new Set();

  for (let i = 0; i < blocks.length; i += 1) {
    const block = blocks[i];
    const category = String(block.category || '').trim();
    if (!category) {
      return { rooms: [], error: `Block ${i + 1}: category name is required` };
    }

    /** @type {string[]} */
    let ids = [];
    if (block.mode === 'custom') {
      ids = block.names || [];
      if (!ids.length) {
        return { rooms: [], error: `Block ${i + 1} (${category}): add at least one custom name` };
      }
    } else {
      const start = block.start;
      const end = block.end;
      if (!Number.isInteger(start) || !Number.isInteger(end) || start < 1 || end < start) {
        return {
          rooms: [],
          error: `Block ${i + 1} (${category}): enter a valid Start–End range (e.g. 101–115)`,
        };
      }
      if (end - start + 1 > 300) {
        return { rooms: [], error: `Block ${i + 1} (${category}): range too large (max 300)` };
      }
      for (let n = start; n <= end; n += 1) ids.push(String(n));
    }

    for (const roomId of ids) {
      if (!isValidRoomDocId(roomId)) {
        return { rooms: [], error: `Invalid room id “${roomId}” in ${category}` };
      }
      if (seen.has(roomId)) {
        return { rooms: [], error: `Duplicate room id “${roomId}” across blocks` };
      }
      seen.add(roomId);
      rooms.push({ roomId, category });
    }
  }

  return { rooms };
}

function updateRoomBlocksSummary(containerId, summaryId) {
  const el = document.getElementById(summaryId);
  if (!el) return;
  const plan = expandRoomBlocks(collectRoomBlocks(containerId));
  if (plan.error) {
    el.textContent = plan.error;
    return;
  }
  const byCat = new Map();
  for (const r of plan.rooms) {
    byCat.set(r.category, (byCat.get(r.category) || 0) + 1);
  }
  const parts = [...byCat.entries()].map(([c, n]) => `${c}: ${n}`);
  el.textContent = plan.rooms.length
    ? `Will create ${plan.rooms.length} room(s) — ${parts.join(' · ')}`
    : 'No rooms configured yet.';
}

/**
 * @param {string} hotelId
 * @param {Array<{ roomId: string, category: string }>} rooms
 */
async function batchCreateRooms(hotelId, rooms) {
  for (let i = 0; i < rooms.length; i += BATCH_LIMIT) {
    const chunk = rooms.slice(i, i + BATCH_LIMIT);
    const batch = writeBatch(db);
    for (const { roomId, category } of chunk) {
      const ref = doc(db, 'Hotels', hotelId, 'Rooms', roomId);
      batch.set(ref, vacantRoomPayload(roomId, category), { merge: true });
    }
    await batch.commit();
  }
  console.log(`[super-admin] Created ${rooms.length} categorized rooms for Hotels/${hotelId}`);
  return rooms;
}

function nextSequentialRoomNumber(existingIds) {
  let max = ROOM_START_NUMBER - 1;
  for (const id of existingIds) {
    if (isNumericRoomId(id)) {
      max = Math.max(max, Number.parseInt(String(id), 10));
    }
  }
  return max + 1;
}

async function syncHotelTotalRooms(hotelId, total) {
  try {
    await updateDoc(doc(db, 'Hotels', hotelId), {
      totalRooms: total,
      updatedAt: serverTimestamp(),
    });
  } catch (err) {
    console.warn('[super-admin] totalRooms sync skipped', err);
  }
}

function roomCategoryOf(room) {
  const raw = String(room?.category || room?.floor || '').trim();
  return raw || UNCATEGORIZED_ROOM_CATEGORY;
}

async function loadEditingHotelRooms(hotelId) {
  const list = document.getElementById('edit-hotel-rooms-list');
  if (list) {
    list.innerHTML = '<p class="empty-state text-xs">Loading rooms…</p>';
  }
  try {
    const snap = await getDocs(collection(db, 'Hotels', hotelId, 'Rooms'));
    const allRooms = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
    const purged = await purgeUnmanagedRooms(hotelId, allRooms);
    editingHotelRooms = allRooms
      .filter((r) => isSuperAdminManagedRoom(r))
      .sort((a, b) => {
        const catCmp = roomCategoryOf(a).localeCompare(roomCategoryOf(b), undefined, {
          sensitivity: 'base',
        });
        if (catCmp !== 0) return catCmp;
        return String(a.id).localeCompare(String(b.id), undefined, { numeric: true });
      });
    renderEditingHotelRooms();
    if (purged > 0) {
      await syncHotelTotalRooms(hotelId, editingHotelRooms.length);
      toast(`Removed ${purged} room(s) not created by Super Admin`);
    }
  } catch (err) {
    console.error('[super-admin] load rooms failed', err);
    toast('Failed to load rooms', 'error');
    if (list) {
      list.innerHTML = '<p class="empty-state text-xs">Failed to load rooms.</p>';
    }
  }
}

function renderEditingHotelRooms() {
  const list = document.getElementById('edit-hotel-rooms-list');
  const countEl = document.getElementById('edit-hotel-rooms-count');
  const addBtn = document.getElementById('edit-hotel-add-rooms-btn');
  if (countEl) countEl.textContent = String(editingHotelRooms.length);
  if (addBtn) addBtn.disabled = editingRoomsBusy || !editingHotelId;
  if (!list) return;

  if (!editingHotelRooms.length) {
    list.innerHTML =
      '<p class="empty-state text-xs">No rooms yet. Use “Add Room Block” to create categorized rooms.</p>';
    return;
  }

  /** @type {Map<string, typeof editingHotelRooms>} */
  const groups = new Map();
  for (const room of editingHotelRooms) {
    const cat = roomCategoryOf(room);
    if (!groups.has(cat)) groups.set(cat, []);
    groups.get(cat).push(room);
  }

  list.innerHTML = [...groups.entries()]
    .map(([category, rooms], groupIndex) => {
      const rows = rooms
        .map((room) => {
          const id = String(room.id);
          const status = String(room.status || (room.occupied ? 'occupied' : 'vacant'));
          const guest = String(room.guestName || '').trim();
          const guestBit =
            guest && guest !== 'Guest' ? ` · ${escapeHtml(guest)}` : '';
          return `
            <div class="edit-hotel-room-row">
              <div class="edit-hotel-room-meta">
                <span class="edit-hotel-room-id">${escapeHtml(formatRoomLabel(id))}</span>
                <span class="edit-hotel-room-status">${escapeHtml(status)}${guestBit}</span>
              </div>
              <div class="edit-hotel-room-btns">
                <button type="button" class="quick-btn" data-room-action="rename" data-room-id="${escapeHtml(id)}" ${editingRoomsBusy ? 'disabled' : ''}>Edit</button>
                <button type="button" class="quick-btn quick-btn-danger" data-room-action="delete" data-room-id="${escapeHtml(id)}" ${editingRoomsBusy ? 'disabled' : ''}>Delete</button>
              </div>
            </div>`;
        })
        .join('');
      return `
        <details class="room-category-group" ${groupIndex === 0 ? 'open' : ''}>
          <summary class="room-category-summary">
            <span class="room-category-summary-title">${escapeHtml(category)}</span>
            <span class="room-category-summary-meta">${rooms.length} room${rooms.length === 1 ? '' : 's'}</span>
          </summary>
          <div class="room-category-body">${rows}</div>
        </details>`;
    })
    .join('');
}

async function onGenerateEditRoomBlock() {
  if (!editingHotelId || editingRoomsBusy) return;
  const plan = expandRoomBlocks(collectRoomBlocks('edit-hotel-room-blocks'));
  if (plan.error) {
    toast(plan.error, 'error');
    return;
  }
  if (!plan.rooms.length) {
    toast('Configure at least one room in the block', 'error');
    return;
  }

  const existing = new Set(editingHotelRooms.map((r) => String(r.id)));
  const clash = plan.rooms.find((r) => existing.has(r.roomId));
  if (clash) {
    toast(`Room “${clash.roomId}” already exists`, 'error');
    return;
  }

  const summary = plan.rooms
    .reduce((acc, r) => {
      acc[r.category] = (acc[r.category] || 0) + 1;
      return acc;
    }, {});
  const summaryText = Object.entries(summary)
    .map(([c, n]) => `${c}: ${n}`)
    .join(', ');
  if (
    !confirm(
      `Create ${plan.rooms.length} room(s) under Hotels/${editingHotelId}/Rooms?\n\n${summaryText}`,
    )
  ) {
    return;
  }

  editingRoomsBusy = true;
  renderEditingHotelRooms();
  try {
    await batchCreateRooms(editingHotelId, plan.rooms);
    hideEditAddBlockPanel();
    await loadEditingHotelRooms(editingHotelId);
    await syncHotelTotalRooms(editingHotelId, editingHotelRooms.length);
    toast(`Added ${plan.rooms.length} room(s)`);
  } catch (err) {
    console.error(err);
    toast(err.message || 'Failed to add rooms', 'error');
  } finally {
    editingRoomsBusy = false;
    renderEditingHotelRooms();
  }
}

async function onRenameRoomClick(oldId) {
  if (!editingHotelId || editingRoomsBusy) return;
  const room = editingHotelRooms.find((r) => String(r.id) === oldId);
  const currentCategory = roomCategoryOf(room || {});

  const rawId = prompt(
    'Room id / name (digits like 101, or a name like Middle East):',
    oldId,
  );
  if (rawId == null) return;
  const newId = normalizeRoom(rawId);
  if (!isValidRoomDocId(newId)) {
    toast('Invalid room id (1–64 chars, no “/”)', 'error');
    return;
  }
  if (newId !== oldId && editingHotelRooms.some((r) => String(r.id) === newId)) {
    toast(`Room “${newId}” already exists`, 'error');
    return;
  }

  const rawCategory = prompt('Category / floor name:', currentCategory);
  if (rawCategory == null) return;
  const newCategory = String(rawCategory).trim() || UNCATEGORIZED_ROOM_CATEGORY;

  if (newId === oldId && newCategory === currentCategory) return;

  if (
    !confirm(
      newId === oldId
        ? `Update category for “${oldId}” → “${newCategory}”?`
        : `Rename “${oldId}” → “${newId}” (category: ${newCategory})?\n\nRenaming copies the doc to a new id and deletes the old one.`,
    )
  ) {
    return;
  }

  editingRoomsBusy = true;
  renderEditingHotelRooms();
  try {
    const oldRef = doc(db, 'Hotels', editingHotelId, 'Rooms', oldId);
    const snap = await getDoc(oldRef);
    if (!snap.exists()) {
      toast('Room no longer exists', 'error');
      await loadEditingHotelRooms(editingHotelId);
      return;
    }
    const data = snap.data() || {};
    const { updatedAt: _drop, ...rest } = data;

    if (newId === oldId) {
      await updateDoc(oldRef, {
        category: newCategory,
        managedBy: SUPER_ADMIN_ROOM_MANAGER,
        roomNumber: oldId,
        updatedAt: serverTimestamp(),
      });
    } else {
      const newRef = doc(db, 'Hotels', editingHotelId, 'Rooms', newId);
      await setDoc(
        newRef,
        {
          ...rest,
          roomNumber: newId,
          category: newCategory,
          managedBy: SUPER_ADMIN_ROOM_MANAGER,
          updatedAt: serverTimestamp(),
        },
        { merge: false },
      );
      await deleteDoc(oldRef);
    }

    await loadEditingHotelRooms(editingHotelId);
    await syncHotelTotalRooms(editingHotelId, editingHotelRooms.length);
    toast(
      newId === oldId
        ? `Updated category → ${newCategory}`
        : `Renamed to ${formatRoomLabel(newId)}`,
    );
  } catch (err) {
    console.error(err);
    toast(err.message || 'Failed to update room', 'error');
  } finally {
    editingRoomsBusy = false;
    renderEditingHotelRooms();
  }
}

async function onDeleteRoomClick(roomId) {
  if (!editingHotelId || editingRoomsBusy) return;
  const room = editingHotelRooms.find((r) => String(r.id) === roomId);
  const status = String(room?.status || '');
  const occupied = Boolean(room?.occupied) || status === 'occupied';
  const warn = occupied
    ? `\n\nWarning: this room looks occupied (${status || 'occupied'}).`
    : '';
  if (
    !confirm(
      `Delete room “${roomId}” from Hotels/${editingHotelId}/Rooms?${warn}\n\nThis cannot be undone.`,
    )
  ) {
    return;
  }

  editingRoomsBusy = true;
  renderEditingHotelRooms();
  try {
    await deleteDoc(doc(db, 'Hotels', editingHotelId, 'Rooms', roomId));
    await loadEditingHotelRooms(editingHotelId);
    await syncHotelTotalRooms(editingHotelId, editingHotelRooms.length);
    toast(`Deleted ${formatRoomLabel(roomId)}`);
  } catch (err) {
    console.error(err);
    toast(err.message || 'Failed to delete room', 'error');
  } finally {
    editingRoomsBusy = false;
    renderEditingHotelRooms();
  }
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

function parseAllowedPackagesInput(raw) {
  return [
    ...new Set(
      String(raw || '')
        .split(/[,\n]+/)
        .map((p) => p.trim())
        .filter(Boolean),
    ),
  ];
}

function syncKioskToggleLabel() {
  const toggle = document.getElementById('kiosk-mode-toggle');
  const label = document.getElementById('kiosk-mode-toggle-label');
  if (!toggle || !label) return;
  label.textContent = toggle.checked ? 'Enabled' : 'Disabled';
  label.classList.toggle('is-inactive', !toggle.checked);
}

/** Always restore Save button to idle default (fixes stuck "Saving..." on re-open). */
function resetKioskSaveButton() {
  const saveBtn = document.getElementById('save-kiosk-settings-btn');
  if (!saveBtn) return;
  saveBtn.innerText = 'Save Changes';
  saveBtn.disabled = false;
  saveBtn.classList.remove('opacity-50', 'cursor-not-allowed');
}

/**
 * Fire-and-forget RTDB mirror — must NEVER block the Save button finally{} path.
 * A hanging RTDB write was the root cause of infinite "Saving...".
 */
function mirrorKioskConfigToRtdb(hotelId, isKioskModeEnabled, allowedPackages, allowOverlayPopups) {
  Promise.resolve()
    .then(async () => {
      const base = `hotels/${hotelId}/config`;
      const overlay =
        typeof allowOverlayPopups === 'boolean' ? allowOverlayPopups : true;
      await Promise.all([
        rtdbSet(rtdbRef(rtdb, `${base}/isKioskModeEnabled`), isKioskModeEnabled),
        rtdbSet(rtdbRef(rtdb, `${base}/allowedPackages`), allowedPackages),
        rtdbSet(rtdbRef(rtdb, `${base}/allowOverlayPopups`), overlay),
        // Legacy snake_case keys (older TV builds).
        rtdbSet(rtdbRef(rtdb, `${base}/is_kiosk_mode_enabled`), isKioskModeEnabled),
        rtdbSet(rtdbRef(rtdb, `${base}/allowed_packages`), allowedPackages),
        rtdbSet(rtdbRef(rtdb, `${base}/allow_overlay_popups`), overlay),
      ]);
      console.log('[super-admin] RTDB kiosk mirror OK →', base);
    })
    .catch((rtdbErr) => {
      console.warn('[super-admin] RTDB kiosk mirror skipped', rtdbErr);
    });
}

function setupKioskSettingsModal() {
  setupModalClose('kiosk-settings-modal', 'kiosk-settings-close');
  document.getElementById('kiosk-settings-close')?.addEventListener('click', () => {
    window.currentKioskHotelId = null;
    kioskHotelId = null;
    resetKioskSaveButton();
  });
  document.getElementById('kiosk-settings-x')?.addEventListener('click', () => {
    closeModal('kiosk-settings-modal');
    window.currentKioskHotelId = null;
    kioskHotelId = null;
    resetKioskSaveButton();
  });

  document.getElementById('kiosk-mode-toggle')?.addEventListener('change', () => {
    syncKioskToggleLabel();
  });

  const saveBtn = document.getElementById('save-kiosk-settings-btn');
  // Bind once — avoid stacking listeners if initSuperAdmin runs again.
  if (saveBtn && saveBtn.dataset.kioskSaveBound !== '1') {
    saveBtn.dataset.kioskSaveBound = '1';
    saveBtn.addEventListener('click', async (e) => {
      e.preventDefault();

      const hotelId =
        window.currentKioskHotelId ||
        kioskHotelId ||
        document.getElementById('kiosk-hotel-id')?.value;

      if (!hotelId) {
        alert('Error: Hotel ID not found!');
        resetKioskSaveButton();
        return;
      }

      const isKioskModeEnabled = Boolean(
        document.getElementById('kiosk-mode-toggle')?.checked,
      );
      const packagesText = document.getElementById('kiosk-packages-input')?.value || '';
      const allowedPackages = packagesText
        .split(',')
        .map((p) => p.trim())
        .filter(Boolean);

      saveBtn.innerText = 'Saving...';
      saveBtn.disabled = true;

      try {
        console.log('Updating Firestore doc:', `Hotels/${hotelId}`, {
          isKioskModeEnabled,
          allowedPackages,
        });

        const hotelRef = doc(db, 'Hotels', hotelId);
        await updateDoc(hotelRef, {
          isKioskModeEnabled,
          allowedPackages,
          updatedAt: new Date().toISOString(),
        });

        console.log('Successfully saved Kiosk settings!');

        // Do not await RTDB — prevents infinite "Saving..." if RTDB hangs.
        const existing = hotelsCache.find((h) => h.id === hotelId);
        const allowOverlay =
          typeof existing?.allowOverlayPopups === 'boolean'
            ? existing.allowOverlayPopups
            : typeof existing?.allow_overlay_popups === 'boolean'
              ? existing.allow_overlay_popups
              : true;
        mirrorKioskConfigToRtdb(hotelId, isKioskModeEnabled, allowedPackages, allowOverlay);

        const cached = hotelsCache.find((h) => h.id === hotelId);
        if (cached) {
          cached.isKioskModeEnabled = isKioskModeEnabled;
          cached.allowedPackages = allowedPackages;
        }

        if (typeof closeModal === 'function') {
          closeModal('kiosk-settings-modal');
        } else {
          document.getElementById('kiosk-settings-modal')?.classList.add('hidden');
        }

        toast('Settings saved successfully!');
        window.currentKioskHotelId = null;
        kioskHotelId = null;
      } catch (error) {
        console.error('Firestore Update Error:', error);
        alert('Failed to save settings: ' + (error?.message || String(error)));
      } finally {
        saveBtn.innerText = 'Save Changes';
        saveBtn.disabled = false;
        saveBtn.classList.remove('opacity-50', 'cursor-not-allowed');
      }
    });
  }
}

/**
 * Open Kiosk Settings for Hotels/{hotelId}.
 * Loads isKioskModeEnabled + allowedPackages from **that hotel document only**.
 * Missing allowedPackages → empty input (never reuse another hotel's list).
 */
async function openKioskSettingsModal(hotelId) {
  if (!hotelId) return;

  window.currentKioskHotelId = hotelId;
  kioskHotelId = hotelId;
  console.log('Opening Kiosk Settings for Hotel ID:', hotelId);

  const nameEl = document.getElementById('kiosk-hotel-name');
  const idField = document.getElementById('kiosk-hotel-id');
  const toggle = document.getElementById('kiosk-mode-toggle');
  const packagesInput = document.getElementById('kiosk-packages-input');

  // Reset Save button BEFORE opening — clears any leftover "Saving..." state.
  resetKioskSaveButton();

  // Always reset UI to empty before load — prevents Treasure Island → Upper Deck bleed in the form.
  if (idField) idField.value = hotelId;
  if (nameEl) nameEl.textContent = hotelId;
  if (toggle) toggle.checked = true;
  if (packagesInput) packagesInput.value = '';
  syncKioskToggleLabel();

  try {
    const snap = await getDoc(doc(db, 'Hotels', hotelId));
    if (!snap.exists()) {
      toast('Hotel not found', 'error');
      window.currentKioskHotelId = null;
      kioskHotelId = null;
      resetKioskSaveButton();
      return;
    }

    const data = snap.data() || {};
    const hotelName = data.name || hotelId;
    const isKioskModeEnabled =
      typeof data.isKioskModeEnabled === 'boolean' ? data.isKioskModeEnabled : true;
    // Strict hotel scope: no global / previous-hotel fallback.
    const allowedPackages = Array.isArray(data.allowedPackages)
      ? data.allowedPackages.map((p) => String(p).trim()).filter(Boolean)
      : [];

    if (nameEl) nameEl.textContent = hotelName;
    if (toggle) toggle.checked = isKioskModeEnabled;
    if (packagesInput) packagesInput.value = allowedPackages.join(', ');
    syncKioskToggleLabel();

    openModal('kiosk-settings-modal');
    resetKioskSaveButton();
  } catch (err) {
    console.error('[super-admin] open kiosk settings failed', err);
    toast(err.message || 'Could not load kiosk settings', 'error');
    window.currentKioskHotelId = null;
    kioskHotelId = null;
    resetKioskSaveButton();
  }
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
