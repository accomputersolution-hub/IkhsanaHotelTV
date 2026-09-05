/**
 * Multi-tenant *public* slug from hostname / query.
 * Subdomain maps to public_hotels/{slug} — never to Hotels/{docId} directly.
 */

export function normalizePublicSlug(raw) {
  return String(raw ?? '')
    .trim()
    .toLowerCase()
    .replace(/-/g, '_')
    .replace(/[^a-z0-9_]/g, '')
    .slice(0, 63);
}

export function extractTenantSlug(hostname = getWindowHostname(), opts = {}) {
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
  if (host.endsWith(suffix)) {
    const sub = host.slice(0, -suffix.length);
    const label = sub.split('.').filter(Boolean)[0];
    if (!label || label === 'www') return null;
    return normalizePublicSlug(label);
  }

  return null;
}

export function resolveTenantSlugFromLocation(location = getWindowLocation(), opts = {}) {
  const fromHost = extractTenantSlug(location?.hostname, opts);
  if (fromHost) return fromHost;

  const search = location?.search || '';
  const params = new URLSearchParams(search.startsWith('?') ? search : `?${search}`);
  const fromQuery =
    params.get('slug') ||
    params.get('public_slug') ||
    params.get('hotel') ||
    params.get('tenant');
  if (fromQuery) return normalizePublicSlug(fromQuery);

  if (opts.fallback) return normalizePublicSlug(opts.fallback);
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
