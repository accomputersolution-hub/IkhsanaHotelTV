/**
 * Lightweight hash router for static hosting.
 * Routes: #/login | #/super-admin | #/pms (hotel modules)
 */

const routeListeners = new Set();

export function getRoute() {
  const hash = (location.hash || '#/login').replace(/^#/, '') || '/login';
  const path = hash.startsWith('/') ? hash : `/${hash}`;
  if (path.startsWith('/super-admin')) return '/super-admin';
  if (path.startsWith('/login')) return '/login';
  // Any hotel PMS module path maps to /pms shell; module id in query or second segment
  if (path.startsWith('/pms') || path === '/' || path === '') return '/pms';
  // Support legacy module deep-links: #/kds etc.
  const mod = path.replace(/^\//, '').split('/')[0];
  if (['pms', 'kds', 'menu', 'messaging', 'housekeeping', 'agenda', 'concierge', 'analytics'].includes(mod)) {
    return '/pms';
  }
  return path;
}

export function getModuleFromRoute() {
  const hash = (location.hash || '').replace(/^#/, '');
  const path = hash.startsWith('/') ? hash : `/${hash}`;
  const parts = path.split('/').filter(Boolean);
  if (parts[0] === 'pms' && parts[1]) return parts[1];
  if (['pms', 'kds', 'menu', 'messaging', 'housekeeping', 'agenda', 'concierge', 'analytics'].includes(parts[0])) {
    return parts[0];
  }
  return localStorage.getItem('activeModule') || 'pms';
}

export function navigateTo(path) {
  const next = path.startsWith('#') ? path : `#${path.startsWith('/') ? path : `/${path}`}`;
  if (location.hash === next) {
    routeListeners.forEach((fn) => fn(getRoute()));
    return;
  }
  location.hash = next;
}

export function onRouteChange(fn) {
  routeListeners.add(fn);
  return () => routeListeners.delete(fn);
}

export function initRouter() {
  window.addEventListener('hashchange', () => {
    const route = getRoute();
    routeListeners.forEach((fn) => fn(route));
  });
  // Also honor /super-admin path if host rewrites into index with that pathname
  if (location.pathname.replace(/\/+$/, '').endsWith('/super-admin') && !location.hash) {
    location.hash = '#/super-admin';
  }
}
