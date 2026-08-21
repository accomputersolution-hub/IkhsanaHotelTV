import { useEffect, useMemo, useState } from 'react';
import { doc, getDoc } from 'firebase/firestore';
import { resolveTenantSlugFromLocation } from '../lib/extractSubdomain.js';

/**
 * Map Firestore Hotels/{id} → branding-friendly tenant object.
 * @param {import('firebase/firestore').Firestore} db
 * @param {string} slug
 */
export async function fetchHotelBySlug(db, slug) {
  const id = String(slug || '').trim();
  if (!id || !db) return null;

  const snap = await getDoc(doc(db, 'Hotels', id));
  if (!snap.exists()) return null;

  const data = snap.data() || {};
  const branding = data.branding || {};
  return {
    id: snap.id,
    name: data.name || snap.id,
    status: data.status || 'active',
    property_type: data.property_type || data.propertyType || 'hotel',
    logoUrl: branding.logoUrl || branding.logo_url || data.logoUrl || '',
    themeColor: branding.themeColor || data.themeColor || '#C9A962',
    bgWallpaper: branding.bgWallpaper || branding.bg_wallpaper || data.bgWallpaper || '',
    branding,
    raw: data,
  };
}

/**
 * Resolve subdomain → Firestore Hotels/{slug} with loading / error state.
 *
 * @param {object} options
 * @param {import('firebase/firestore').Firestore} options.db
 * @param {string} [options.rootDomain='hostity.in']
 * @param {string|null} [options.fallback]  Used when hostname has no subdomain
 * @param {boolean} [options.useDefaultOnLocal=true]  localhost → DEFAULT_HOTEL_ID if no ?hotel=
 *
 * @returns {{
 *   status: 'loading'|'ready'|'not_found'|'missing_slug'|'error',
 *   slug: string|null,
 *   hotel: object|null,
 *   error: string|null,
 *   reload: () => void,
 * }}
 *
 * @example
 *   const { status, hotel, slug } = useHotelTenant({ db });
 *   if (status === 'loading') return <LoadingScreen />;
 *   if (status !== 'ready') return <TenantErrorScreen status={status} slug={slug} />;
 */
export function useHotelTenant({
  db,
  rootDomain = 'hostity.in',
  fallback = null,
  useDefaultOnLocal = true,
} = {}) {
  const slug = useMemo(
    () =>
      resolveTenantSlugFromLocation(window.location, {
        rootDomain,
        fallback,
        useDefaultOnLocal,
      }),
    [rootDomain, fallback, useDefaultOnLocal],
  );

  const [tick, setTick] = useState(0);
  const [state, setState] = useState({
    status: 'loading',
    slug,
    hotel: null,
    error: null,
  });

  useEffect(() => {
    let cancelled = false;

    async function load() {
      if (!slug) {
        if (!cancelled) {
          setState({
            status: 'missing_slug',
            slug: null,
            hotel: null,
            error: 'No hotel subdomain found. Open https://{slug}.hostity.in or use ?hotel=slug on localhost.',
          });
        }
        return;
      }

      if (!db) {
        if (!cancelled) {
          setState({
            status: 'error',
            slug,
            hotel: null,
            error: 'Firestore is not configured',
          });
        }
        return;
      }

      if (!cancelled) {
        setState((prev) => ({
          ...prev,
          status: 'loading',
          slug,
          error: null,
        }));
      }

      try {
        const hotel = await fetchHotelBySlug(db, slug);
        if (cancelled) return;

        if (!hotel) {
          setState({
            status: 'not_found',
            slug,
            hotel: null,
            error: `Hotel “${slug}” was not found in Firestore Hotels/${slug}`,
          });
          return;
        }

        // Apply CSS variables for kiosk chrome
        try {
          const root = document.documentElement;
          if (hotel.themeColor) root.style.setProperty('--hotel-theme', hotel.themeColor);
          if (hotel.bgWallpaper) {
            root.style.setProperty('--hotel-wallpaper', `url("${hotel.bgWallpaper}")`);
          }
          if (hotel.logoUrl) root.style.setProperty('--hotel-logo', `url("${hotel.logoUrl}")`);
          document.title = hotel.name ? `${hotel.name} · Kiosk` : document.title;
        } catch {
          /* ignore DOM branding failures */
        }

        setState({
          status: 'ready',
          slug,
          hotel,
          error: null,
        });
      } catch (err) {
        if (cancelled) return;
        setState({
          status: 'error',
          slug,
          hotel: null,
          error: err?.message || 'Failed to load hotel',
        });
      }
    }

    load();
    return () => {
      cancelled = true;
    };
  }, [db, slug, tick]);

  return {
    ...state,
    reload: () => setTick((n) => n + 1),
  };
}
