/**
 * TenantManager — single source of truth for active / impersonated hotel.
 * All PMS listeners must use getHotelId() and subscribe via onHotelChange / onTenantChange.
 * Firestore root is always capital-H: Hotels/{hotelId}/…
 * Slugs use underscores (ikhsana_001), never hyphens.
 */

import { normalizeHotelId } from './firebase-config.js';

const STORAGE_KEY = 'activeHotelId';
const META_KEY = 'activeHotelMeta';
const IMPERSONATE_KEY = 'impersonatingHotelId';

let hotelId = (() => {
  const raw = localStorage.getItem(STORAGE_KEY) || '';
  if (!raw.trim()) return '';
  return normalizeHotelId(raw);
})();
let hotelMeta = safeParse(localStorage.getItem(META_KEY), null);
let impersonating = Boolean(localStorage.getItem(IMPERSONATE_KEY));
const listeners = new Set();

// Persist migrated underscore slug if localStorage still had a hyphenated id
if (hotelId && localStorage.getItem(STORAGE_KEY) !== hotelId) {
  localStorage.setItem(STORAGE_KEY, hotelId);
}

function safeParse(raw, fallback) {
  try {
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

function notify() {
  listeners.forEach((fn) => {
    try {
      fn(hotelId, hotelMeta);
    } catch (err) {
      console.error('[TenantManager] listener error', err);
    }
  });
}

/**
 * @param {string} id
 * @param {{ name?: string, themeColor?: string, logoUrl?: string, bgWallpaper?: string, branding?: object } | null} [meta]
 * @param {{ impersonate?: boolean }} [opts]
 */
function setHotelContext(id, meta = null, opts = {}) {
  const trimmed = String(id || '').trim();
  const next = trimmed ? normalizeHotelId(trimmed) : '';
  const changed = next !== hotelId;
  hotelId = next;

  const branding = meta?.branding || {};
  if (meta) {
    hotelMeta = {
      ...hotelMeta,
      ...meta,
      hotelId: next,
      logoUrl: meta.logoUrl || branding.logoUrl || hotelMeta?.logoUrl || '',
      themeColor: meta.themeColor || branding.themeColor || hotelMeta?.themeColor || '',
      bgWallpaper: meta.bgWallpaper || branding.bgWallpaper || hotelMeta?.bgWallpaper || '',
      name: meta.name || hotelMeta?.name || next,
    };
  } else if (!next) {
    hotelMeta = null;
  } else if (hotelMeta?.hotelId !== next) {
    hotelMeta = { ...(hotelMeta || {}), hotelId: next };
  }

  if (opts.impersonate && next) {
    impersonating = true;
    localStorage.setItem(IMPERSONATE_KEY, next);
  } else if (!next) {
    impersonating = false;
    localStorage.removeItem(IMPERSONATE_KEY);
  }

  if (next) localStorage.setItem(STORAGE_KEY, next);
  else localStorage.removeItem(STORAGE_KEY);

  if (hotelMeta) localStorage.setItem(META_KEY, JSON.stringify(hotelMeta));
  else localStorage.removeItem(META_KEY);

  if (changed) notify();
  applyBranding(hotelMeta);
  return hotelId;
}

/**
 * Merge fields into current hotel meta without changing hotelId.
 * Used for live status / branding sync from Hotels/{id} snapshots.
 */
export function updateHotelMeta(partial = {}) {
  if (!hotelId) return null;
  hotelMeta = {
    ...(hotelMeta || {}),
    ...partial,
    hotelId,
  };
  localStorage.setItem(META_KEY, JSON.stringify(hotelMeta));
  applyBranding(hotelMeta);
  return hotelMeta;
}

export function getHotelStatus() {
  return hotelMeta?.status || 'active';
}

export function isHotelInactive() {
  return String(getHotelStatus()).toLowerCase() === 'inactive';
}

export function getHotelId() {
  return hotelId;
}

export function getHotelMeta() {
  return hotelMeta;
}

export function hasHotelContext() {
  return Boolean(hotelId);
}

export function isImpersonating() {
  return impersonating && Boolean(hotelId);
}

export function clearHotelContext() {
  impersonating = false;
  localStorage.removeItem(IMPERSONATE_KEY);
  return setHotelContext('', null);
}

/** Subscribe to hotel switches. Invokes immediately with current id. Returns unsubscribe. */
export function onHotelChange(fn) {
  listeners.add(fn);
  try {
    fn(hotelId, hotelMeta);
  } catch (err) {
    console.error('[TenantManager] listener error', err);
  }
  return () => listeners.delete(fn);
}

export const onTenantChange = onHotelChange;

/**
 * Spec API: Super Admin selects a hotel → all Rooms/KDS/Menu/Alerts/Requests
 * listeners re-bind to Hotels/{selectedHotelId}/…
 */
export function setImpersonatedHotel(selectedHotelId, meta = null) {
  return setHotelContext(selectedHotelId, meta, { impersonate: true });
}

/** Bind hotel admin to their own hotel (not impersonation). */
export function setAssignedHotel(selectedHotelId, meta = null) {
  impersonating = false;
  localStorage.removeItem(IMPERSONATE_KEY);
  return setHotelContext(selectedHotelId, meta, { impersonate: false });
}

export const TenantManager = {
  getHotelId,
  getHotelMeta,
  getHotelStatus,
  isHotelInactive,
  hasHotelContext,
  isImpersonating,
  setHotelContext,
  setImpersonatedHotel,
  setAssignedHotel,
  updateHotelMeta,
  clearHotelContext,
  onHotelChange,
  onTenantChange,
  /** Strict Android-parity root */
  hotelsCollection: 'Hotels',
  pathFor(subcollection, id = hotelId) {
    if (!id) throw new Error('No active hotel context');
    return `Hotels/${normalizeHotelId(id)}/${subcollection}`;
  },
};

export function applyBranding(meta) {
  const root = document.documentElement;
  const logoTitle = document.querySelector('.logo-title');
  const emblem = document.querySelector('.logo-emblem');
  const tenantBanner = document.getElementById('tenant-banner');
  const tenantNameEl = document.getElementById('tenant-banner-name');

  if (meta?.themeColor) {
    root.style.setProperty('--gold', meta.themeColor);
  }

  if (meta?.name && logoTitle) {
    logoTitle.innerHTML = escapeBranding(meta.name).replace(/\s+/g, '<br />');
  }
  if (emblem && meta?.logoUrl) {
    emblem.innerHTML = `<img src="${escapeAttr(meta.logoUrl)}" alt="" class="logo-emblem-img" />`;
  }

  if (tenantBanner && tenantNameEl) {
    if (meta?.name || hotelId) {
      tenantBanner.classList.remove('hidden');
      tenantNameEl.textContent = meta?.name || hotelId;
    }
  }

  if (meta?.bgWallpaper) {
    document.body.style.backgroundImage = `linear-gradient(rgba(11,19,37,0.85), rgba(11,19,37,0.92)), url(${JSON.stringify(meta.bgWallpaper)})`;
    document.body.style.backgroundSize = 'cover';
  }
}

function escapeBranding(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function escapeAttr(s) {
  return String(s).replace(/"/g, '&quot;');
}

// Re-export setHotelContext for existing imports
export { setHotelContext };
