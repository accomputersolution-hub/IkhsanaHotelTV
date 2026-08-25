/**
 * Load public hotel branding for subdomain tenants via public_hotels/{slug}.
 * Does not read private Hotels/{id} fields (admin email, rooms, …).
 */

import { doc, getDoc } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import { db } from './firebase-config.js';
import { resolveTenantSlugFromLocation, normalizePublicSlug } from './tenant-slug.js';
import { setAssignedHotel, updateHotelMeta } from './tenant-context.js';

export async function fetchPublicHotelBySlug(slug) {
  const id = normalizePublicSlug(slug);
  if (!id) return null;

  const snap = await getDoc(doc(db, 'public_hotels', id));
  if (!snap.exists()) return null;

  const data = snap.data() || {};
  const hotelId = data.hotelId || data.hotel_id || '';
  if (!hotelId) return null;

  return {
    publicSlug: id,
    hotelId,
    name: data.name || id,
    status: data.status || 'active',
    property_type: data.property_type || 'hotel',
    logoUrl: data.logoUrl || data.logo_url || '',
    themeColor: data.themeColor || '',
    bgWallpaper: data.bgWallpaper || data.bg_wallpaper || '',
    bgWallpaperDark: data.bgWallpaperDark || data.bg_wallpaper_dark || '',
  };
}

/** @deprecated Use fetchPublicHotelBySlug */
export async function fetchHotelBySlug(slug) {
  return fetchPublicHotelBySlug(slug);
}

export async function bootstrapTenantFromHostname(opts = {}) {
  const slug = resolveTenantSlugFromLocation(undefined, {
    fallback: opts.fallback ?? null,
  });

  if (!slug) {
    return { slug: null, hotel: null, status: 'missing_slug' };
  }

  try {
    const hotel = await fetchPublicHotelBySlug(slug);
    if (!hotel) {
      return { slug, hotel: null, status: 'not_found' };
    }

    // Bind internal hotelId for staff PMS; branding comes from public doc
    setAssignedHotel(hotel.hotelId, {
      name: hotel.name,
      logoUrl: hotel.logoUrl,
      themeColor: hotel.themeColor,
      bgWallpaper: hotel.bgWallpaper,
      property_type: hotel.property_type,
      status: hotel.status,
      publicSlug: hotel.publicSlug,
    });
    updateHotelMeta({
      name: hotel.name,
      logoUrl: hotel.logoUrl,
      themeColor: hotel.themeColor,
      bgWallpaper: hotel.bgWallpaper,
      property_type: hotel.property_type,
      status: hotel.status,
      publicSlug: hotel.publicSlug,
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
