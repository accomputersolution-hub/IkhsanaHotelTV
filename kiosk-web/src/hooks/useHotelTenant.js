import { useEffect, useMemo, useState } from 'react';
import { resolvePublicSlugFromLocation } from '../lib/extractSubdomain.js';
import { fetchPublicHotelConfig } from '../lib/fetchPublicHotelConfig.js';

/**
 * Secure public-config bootstrap for kiosk:
 * subdomain → public_hotels/{slug} (logo / name / theme only).
 *
 * Does NOT read Hotels/{id} Rooms, guests, or admin fields.
 */
export function useHotelTenant({
  db,
  rootDomain = 'pcncloud.in',
  fallback = null,
  useDefaultOnLocal = false,
} = {}) {
  const slug = useMemo(
    () =>
      resolvePublicSlugFromLocation(window.location, {
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
            error:
              'No public hotel slug. Open https://{slug}.pcncloud.in or use ?slug=ikhsana on localhost.',
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
        setState((prev) => ({ ...prev, status: 'loading', slug, error: null }));
      }

      try {
        const hotel = await fetchPublicHotelConfig(db, slug);
        if (cancelled) return;

        if (!hotel || !hotel.hotelId) {
          setState({
            status: 'not_found',
            slug,
            hotel: null,
            error: `No public hotel for slug “${slug}” (public_hotels/${slug})`,
          });
          return;
        }

        if (String(hotel.status).toLowerCase() === 'inactive') {
          setState({
            status: 'error',
            slug,
            hotel: null,
            error: 'This property is inactive. Contact your administrator.',
          });
          return;
        }

        try {
          const root = document.documentElement;
          if (hotel.themeColor) root.style.setProperty('--hotel-theme', hotel.themeColor);
          if (hotel.bgWallpaper) {
            root.style.setProperty('--hotel-wallpaper', `url("${hotel.bgWallpaper}")`);
          }
          document.title = hotel.name ? `${hotel.name} · Kiosk` : document.title;
        } catch {
          /* ignore */
        }

        setState({ status: 'ready', slug, hotel, error: null });
      } catch (err) {
        if (cancelled) return;
        setState({
          status: 'error',
          slug,
          hotel: null,
          error: err?.message || 'Failed to load public hotel config',
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

/** @deprecated Prefer fetchPublicHotelConfig */
export async function fetchHotelBySlug(db, slug) {
  return fetchPublicHotelConfig(db, slug);
}
