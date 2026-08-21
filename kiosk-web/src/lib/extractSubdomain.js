/**
 * Pure subdomain / query slug helpers for React kiosk (no Firebase imports).
 * Keep in sync with admin-panel/js/tenant-slug.js
 */

export const DEFAULT_HOTEL_ID = 'ikhsana_001';

export function normalizeHotelId(raw) {
  const cleaned = String(raw ?? '')
    .trim()
    .toLowerCase()
    .replace(/-/g, '_');
  if (!cleaned || cleaned === 'ikhsana' || cleaned === 'ikhsana001') {
    return DEFAULT_HOTEL_ID;
  }
  return cleaned;
}

/**
 * Extract tenant slug from hostname.
 * ikhsana_001.hostity.in → ikhsana_001
 * localhost → null
 */
export function extractTenantSlug(hostname, opts = {}) {
  const rootDomain = String(opts.rootDomain || 'hostity.in')
    .trim()
    .toLowerCase()
    .replace(/^www\./, '');

  const host = String(hostname || '')
    .trim()
    .toLowerCase()
    .split(':')[0];

  if (!host) return null;

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

  if (host === rootDomain || host === `www.${rootDomain}`) return null;

  const suffix = `.${rootDomain}`;
  if (host.endsWith(suffix)) {
    const sub = host.slice(0, -suffix.length);
    const label = sub.split('.').filter(Boolean)[0];
    if (!label || label === 'www') return null;
    return normalizeHotelId(label);
  }

  if (host.endsWith('.vercel.app')) return null;
  return null;
}

export function isLocalHostname(hostname) {
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

/**
 * Priority: subdomain → ?hotel= → fallback / local default.
 */
export function resolveTenantSlugFromLocation(location = window.location, opts = {}) {
  const fromHost = extractTenantSlug(location?.hostname, opts);
  if (fromHost) return fromHost;

  const params = new URLSearchParams(location?.search || '');
  const fromQuery =
    params.get('hotel') ||
    params.get('hotelId') ||
    params.get('hotel_id') ||
    params.get('tenant');
  if (fromQuery) return normalizeHotelId(fromQuery);

  if (opts.fallback) return normalizeHotelId(opts.fallback);
  if (opts.useDefaultOnLocal && isLocalHostname(location?.hostname)) {
    return DEFAULT_HOTEL_ID;
  }
  return null;
}
