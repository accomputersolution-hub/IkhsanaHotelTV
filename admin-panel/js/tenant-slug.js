/**
 * Multi-tenant slug from hostname / query string.
 *
 * Production:  ikhsana_001.hostity.in  →  ikhsana_001
 * Localdev:    localhost?hotel=ikhsana_001  →  ikhsana_001
 *
 * Slugs use underscores (matches Firestore Hotels/{hotelId} + Android).
 */

import { DEFAULT_HOTEL_ID, normalizeHotelId } from './firebase-config.js';

/** Apex / platform hosts that are NOT a tenant subdomain. */
export const PLATFORM_ROOT_DOMAINS = Object.freeze([
  'hostity.in',
  'www.hostity.in',
]);

/**
 * Extract the tenant label from a hostname.
 *
 * @param {string} [hostname]
 * @param {{ rootDomain?: string }} [opts]  e.g. { rootDomain: 'hostity.in' }
 * @returns {string|null} normalized hotel slug, or null if apex / localhost / unknown
 *
 * @example
 *   extractTenantSlug('ikhsana_001.hostity.in')     // 'ikhsana_001'
 *   extractTenantSlug('treasure-island.hostity.in') // 'treasure_island'
 *   extractTenantSlug('localhost')                  // null
 *   extractTenantSlug('hostity.in')                 // null
 */
export function extractTenantSlug(hostname = getWindowHostname(), opts = {}) {
  const rootDomain = String(opts.rootDomain || 'hostity.in')
    .trim()
    .toLowerCase()
    .replace(/^www\./, '');

  const host = String(hostname || '')
    .trim()
    .toLowerCase()
    .split(':')[0]; // strip port

  if (!host) return null;

  // Local / loopback — no subdomain tenant
  if (
    host === 'localhost' ||
    host === '127.0.0.1' ||
    host === '0.0.0.0' ||
    host === '::1' ||
    host.endsWith('.localhost') ||
    host.endsWith('.local')
  ) {
    return null;
  }

  // Apex + www of the SaaS domain
  if (host === rootDomain || host === `www.${rootDomain}`) {
    return null;
  }

  // *.hostity.in → leftmost label is the hotel slug
  const suffix = `.${rootDomain}`;
  if (host.endsWith(suffix)) {
    const sub = host.slice(0, -suffix.length);
    const label = sub.split('.').filter(Boolean)[0]; // ignore nested preview labels
    if (!label || label === 'www') return null;
    return normalizeHotelId(label);
  }

  // Vercel preview / project URLs are not hotel tenants
  if (host.endsWith('.vercel.app')) {
    return null;
  }

  return null;
}

/**
 * Resolve hotel slug from the current page location.
 * Priority: subdomain → ?hotel= / ?hotelId= / ?tenant= → optional default.
 *
 * @param {Location|{hostname?:string,search?:string}} [location]
 * @param {{ rootDomain?: string, fallback?: string|null, useDefaultOnLocal?: boolean }} [opts]
 * @returns {string|null}
 */
export function resolveTenantSlugFromLocation(location = getWindowLocation(), opts = {}) {
  const fromHost = extractTenantSlug(location?.hostname, opts);
  if (fromHost) return fromHost;

  const search = location?.search || '';
  const params = new URLSearchParams(search.startsWith('?') ? search : `?${search}`);
  const fromQuery =
    params.get('hotel') ||
    params.get('hotelId') ||
    params.get('hotel_id') ||
    params.get('tenant');
  if (fromQuery) return normalizeHotelId(fromQuery);

  if (opts.fallback != null && opts.fallback !== '') {
    return normalizeHotelId(opts.fallback);
  }

  // Local-only convenience: use DEFAULT_HOTEL_ID when explicitly enabled
  if (opts.useDefaultOnLocal && isLocalHostname(location?.hostname)) {
    return DEFAULT_HOTEL_ID;
  }

  return null;
}

export function isLocalHostname(hostname = getWindowHostname()) {
  const host = String(hostname || '')
    .toLowerCase()
    .split(':')[0];
  return (
    host === 'localhost' ||
    host === '127.0.0.1' ||
    host === '0.0.0.0' ||
    host === '::1' ||
    host.endsWith('.localhost') ||
    host.endsWith('.local')
  );
}

function getWindowHostname() {
  try {
    return typeof window !== 'undefined' ? window.location.hostname : '';
  } catch {
    return '';
  }
}

function getWindowLocation() {
  try {
    return typeof window !== 'undefined' ? window.location : { hostname: '', search: '' };
  } catch {
    return { hostname: '', search: '' };
  }
}
