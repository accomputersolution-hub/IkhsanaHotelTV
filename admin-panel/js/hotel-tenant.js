/**
 * Load Hotels/{slug} branding for subdomain (or query) tenants.
 */

import { doc, getDoc } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import { db } from './firebase-config.js';
import { normalizeHotelId } from './firebase-config.js';
import { resolveTenantSlugFromLocation } from './tenant-slug.js';
import { setAssignedHotel, updateHotelMeta } from './tenant-context.js';

/**
 * @typedef {object} HotelTenant
 * @property {string} id
 * @property {string} name
 * @property {string} status
 * @property {string} property_type
 * @property {string} logoUrl
 * @property {string} themeColor
 * @property {string} bgWallpaper
 * @property {object} branding
 * @property {object} raw
 */

/**
 * Fetch a single hotel document by slug / document id.
 * @param {string} slug
 * @returns {Promise<HotelTenant|null>}
 */
export async function fetchHotelBySlug(slug) {
  const id = normalizeHotelId(slug);
  if (!id) return null;

  const snap = await getDoc(doc(db, 'Hotels', id));
  if (!snap.exists()) return null;

  const data = snap.data() || {};
  const branding = data.branding || {};
  return {
    id,
    name: data.name || id,
    status: data.status || 'active',
    property_type: data.property_type || data.propertyType || 'hotel',
    logoUrl: branding.logoUrl || branding.logo_url || data.logoUrl || '',
    themeColor: branding.themeColor || data.themeColor || '',
    bgWallpaper: branding.bgWallpaper || branding.bg_wallpaper || data.bgWallpaper || '',
    branding,
    adminEmail: data.adminEmail || '',
    isKioskModeEnabled: data.isKioskModeEnabled !== false,
    raw: data,
  };
}

/**
 * Resolve slug from the URL and bind TenantManager + branding.
 * Safe to call on admin / kiosk boot.
 *
 * @param {{ useDefaultOnLocal?: boolean, fallback?: string|null }} [opts]
 * @returns {Promise<{ slug: string|null, hotel: HotelTenant|null, status: 'ready'|'not_found'|'missing_slug'|'error', error?: string }>}
 */
export async function bootstrapTenantFromHostname(opts = {}) {
  const slug = resolveTenantSlugFromLocation(undefined, {
    useDefaultOnLocal: opts.useDefaultOnLocal === true,
    fallback: opts.fallback ?? null,
  });

  if (!slug) {
    return { slug: null, hotel: null, status: 'missing_slug' };
  }

  try {
    const hotel = await fetchHotelBySlug(slug);
    if (!hotel) {
      return { slug, hotel: null, status: 'not_found' };
    }

    setAssignedHotel(hotel.id, {
      name: hotel.name,
      logoUrl: hotel.logoUrl,
      themeColor: hotel.themeColor,
      bgWallpaper: hotel.bgWallpaper,
      property_type: hotel.property_type,
      status: hotel.status,
      branding: hotel.branding,
    });
    updateHotelMeta({
      name: hotel.name,
      logoUrl: hotel.logoUrl,
      themeColor: hotel.themeColor,
      bgWallpaper: hotel.bgWallpaper,
      property_type: hotel.property_type,
      status: hotel.status,
    });

    return { slug, hotel, status: 'ready' };
  } catch (err) {
    console.error('[hotel-tenant] bootstrap failed', err);
    return {
      slug,
      hotel: null,
      status: 'error',
      error: err?.message || 'Failed to load hotel',
    };
  }
}
