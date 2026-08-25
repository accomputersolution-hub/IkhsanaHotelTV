import {
  collection,
  query,
  where,
  limit,
  getDocs,
  doc,
  getDoc,
} from 'firebase/firestore';
import { normalizePublicSlug } from '../lib/extractSubdomain.js';

/**
 * Public branding only — never includes rooms, guests, admin emails, etc.
 * Source of truth for anonymous kiosk: collection `public_hotels/{publicSlug}`
 * (mirrored from Hotels/{hotelId} on onboard / branding update).
 *
 * @typedef {object} PublicHotelConfig
 * @property {string} publicSlug
 * @property {string} hotelId   Internal id — used only after pairing for scoped paths
 * @property {string} name
 * @property {string} logoUrl
 * @property {string} themeColor
 * @property {string} bgWallpaper
 * @property {string} property_type
 * @property {string} status
 */

/**
 * Fetch public hotel config by marketing slug.
 * Prefer `public_hotels/{slug}`; fall back to Hotels where public_slug == slug
 * (only works if rules allow that query — prefer the public collection).
 *
 * @param {import('firebase/firestore').Firestore} db
 * @param {string} publicSlug
 * @returns {Promise<PublicHotelConfig|null>}
 */
export async function fetchPublicHotelConfig(db, publicSlug) {
  const slug = normalizePublicSlug(publicSlug);
  if (!slug || !db) return null;

  // 1) Dedicated public directory (field-safe — doc only has public fields)
  const publicRef = doc(db, 'public_hotels', slug);
  const publicSnap = await getDoc(publicRef);
  if (publicSnap.exists()) {
    return mapPublicDoc(slug, publicSnap.data() || {});
  }

  // 2) Fallback query (requires composite rules + index). Prefer avoiding this
  //    in production because Hotels/{id} may contain sensitive fields.
  const q = query(
    collection(db, 'Hotels'),
    where('public_slug', '==', slug),
    limit(1),
  );
  const result = await getDocs(q);
  if (result.empty) return null;

  const hotelDoc = result.docs[0];
  const data = hotelDoc.data() || {};
  const branding = data.branding || {};
  return {
    publicSlug: slug,
    hotelId: hotelDoc.id,
    name: data.name || slug,
    logoUrl: branding.logoUrl || branding.logo_url || '',
    themeColor: branding.themeColor || '#C9A962',
    bgWallpaper: branding.bgWallpaper || branding.bg_wallpaper || '',
    bgWallpaperDark: branding.bgWallpaperDark || branding.bg_wallpaper_dark || '',
    property_type: data.property_type || 'hotel',
    status: data.status || 'active',
  };
}

function mapPublicDoc(slug, data) {
  return {
    publicSlug: slug,
    hotelId: data.hotelId || data.hotel_id || '',
    name: data.name || slug,
    logoUrl: data.logoUrl || data.logo_url || '',
    themeColor: data.themeColor || '#C9A962',
    bgWallpaper: data.bgWallpaper || data.bg_wallpaper || '',
    bgWallpaperDark: data.bgWallpaperDark || data.bg_wallpaper_dark || '',
    property_type: data.property_type || 'hotel',
    status: data.status || 'active',
  };
}
