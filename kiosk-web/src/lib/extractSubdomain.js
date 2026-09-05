/**
 * Public subdomain slug (NOT the internal Hotels/{hotelId} document id).
 * Avoids IDOR: kiosk never needs the private hotel document id in the URL.
 */

/** Platform hosts that must never resolve as a hotel public slug. */
const RESERVED_SUBDOMAINS = new Set([
  'www',
  'go',
  'admin',
  'app',
  'api',
  'mail',
  'smtp',
  'ftp',
  'cdn',
  'static',
  'status',
  'docs',
  'dev',
  'staging',
  'preview',
  'vercel',
]);

export function normalizePublicSlug(raw) {
  return String(raw ?? '')
    .trim()
    .toLowerCase()
    .replace(/-/g, '_')
    .replace(/[^a-z0-9_]/g, '')
    .slice(0, 63);
}

/**
 * Extract public marketing slug from hostname.
 * ikhsana.pcncloud.in → "ikhsana"
 * go.pcncloud.in / www.pcncloud.in → null
 * localhost → null
 */
export function extractPublicSlug(hostname, opts = {}) {
  const rootDomain = String(opts.rootDomain || 'pcncloud.in')
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
  if (host.endsWith('.vercel.app')) return null;

  const suffix = `.${rootDomain}`;
  if (!host.endsWith(suffix)) return null;

  const sub = host.slice(0, -suffix.length);
  const label = sub.split('.').filter(Boolean)[0];
  if (!label || RESERVED_SUBDOMAINS.has(label)) return null;
  return normalizePublicSlug(label);
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
 * Priority: subdomain → ?slug= / ?hotel= → optional fallback.
 * Query param is the *public* slug, never the internal hotelId.
 */
export function resolvePublicSlugFromLocation(location = window.location, opts = {}) {
  const fromHost = extractPublicSlug(location?.hostname, opts);
  if (fromHost) return fromHost;

  const params = new URLSearchParams(location?.search || '');
  const fromQuery =
    params.get('slug') ||
    params.get('public_slug') ||
    params.get('hotel') ||
    params.get('tenant');
  if (fromQuery) return normalizePublicSlug(fromQuery);

  if (opts.fallback) return normalizePublicSlug(opts.fallback);
  return null;
}

/** @deprecated Use resolvePublicSlugFromLocation — kept for older imports */
export function resolveTenantSlugFromLocation(location, opts) {
  return resolvePublicSlugFromLocation(location, opts);
}

/** @deprecated Use extractPublicSlug */
export function extractTenantSlug(hostname, opts) {
  return extractPublicSlug(hostname, opts);
}

/** @deprecated Do not use internal hotel id normalizer for public URLs */
export function normalizeHotelId(raw) {
  return normalizePublicSlug(raw);
}
