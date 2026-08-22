import { initAudio, setConnectionStatus, toast } from './utils.js';
import { initOrders } from './orders.js';
import { initAlerts } from './alerts.js';
import { initMenu } from './menu.js';
import { initGuests } from './guests.js';
import { initHousekeeping, initConcierge } from './requests.js?v=20260819';
import { initEmergencyContacts } from './emergency-contacts.js';
import { initDailyAgenda } from './daily-agenda.js';
import { initAnnouncement } from './announcement.js';
import { initAnalytics } from './analytics.js';
import { initNavigation, showModule, setHotelChromeVisible } from './navigation.js';
import { initStaffManagement } from './staff.js';
import { initPairingClaim } from './pairing.js';
import {
  canAccessPropertyPms,
  getDefaultModuleForRole,
  resolveAllowedModule,
  applyRbacNavChrome,
  getOperationalRole,
} from './rbac.js';
import {
  initAuth,
  loginWithEmail,
  logout,
  isSuperAdmin,
  getCurrentProfile,
  ensureSuperAdminProfile,
  needsBootstrap,
  isAuthLoading,
  forceAuthReady,
} from './auth.js';
import {
  getHotelId,
  hasHotelContext,
  setAssignedHotel,
  getHotelMeta,
  isImpersonating,
  updateHotelMeta,
  onHotelChange,
  clearHotelContext,
} from './tenant-context.js';
import { extractTenantSlug } from './tenant-slug.js';
import { bootstrapTenantFromHostname } from './hotel-tenant.js';
import { initSuperAdmin, startSuperAdminListeners, stopSuperAdminListeners } from './super-admin.js';
import { initRouter, onRouteChange, navigateTo, getRoute, getModuleFromRoute } from './router.js';
import { db } from './firebase-config.js';
import { doc, getDoc, onSnapshot } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import './paths.js';

/** @type {(() => void) | null} */
let hotelStatusUnsub = null;
/** @type {string} */
let cachedHotelStatus = 'active';

function setAuthBootUi(loading) {
  const loader = document.getElementById('auth-boot-loader');
  if (loader) {
    loader.classList.toggle('hidden', !loading);
    loader.setAttribute('aria-busy', loading ? 'true' : 'false');
  }
  document.body.classList.toggle('auth-booting', loading);
  if (loading) {
    document.getElementById('login-shell')?.classList.add('hidden');
    document.getElementById('super-admin-shell')?.classList.add('hidden');
    document.getElementById('app-shell')?.classList.add('hidden');
  }
}

/** Always drop the boot gate once auth is no longer loading. */
function clearAuthBootUi() {
  if (!isAuthLoading()) {
    setAuthBootUi(false);
  }
}

function showShell(which) {
  // Never reveal app shells while Firebase session is still restoring
  if (isAuthLoading()) {
    setAuthBootUi(true);
    return;
  }
  setAuthBootUi(false);
  document.getElementById('login-shell')?.classList.toggle('hidden', which !== 'login');
  document.getElementById('super-admin-shell')?.classList.toggle('hidden', which !== 'super-admin');
  document.getElementById('app-shell')?.classList.toggle('hidden', which !== 'pms');
}

function isInactiveStatus(status) {
  return String(status || 'active').toLowerCase() === 'inactive';
}

function showDeactivatedGate(show, hotelLabel = '') {
  const gate = document.getElementById('account-deactivated-gate');
  if (!gate) return;
  gate.classList.toggle('hidden', !show);
  const hotelEl = document.getElementById('account-deactivated-hotel');
  if (hotelEl) hotelEl.textContent = show ? hotelLabel : '';
  // Dim PMS chrome while blocked so nothing behind is interactive
  document.getElementById('app-shell')?.classList.toggle('pms-locked', show);
}

function stopHotelStatusWatch() {
  if (hotelStatusUnsub) {
    hotelStatusUnsub();
    hotelStatusUnsub = null;
  }
}

/**
 * Live listen to Hotels/{hotelId}.status so deactivation takes effect immediately.
 * @param {string} hotelId
 * @param {(status: string, data: object) => void} onStatus
 */
function startHotelStatusWatch(hotelId, onStatus) {
  stopHotelStatusWatch();
  if (!hotelId) {
    cachedHotelStatus = 'active';
    return;
  }
  hotelStatusUnsub = onSnapshot(
    doc(db, 'Hotels', hotelId),
    (snap) => {
      const data = snap.exists() ? snap.data() || {} : {};
      const status = data.status || 'active';
      cachedHotelStatus = status;
      updateHotelMeta({
        name: data?.name || hotelId,
        status,
        logoUrl: data?.branding?.logoUrl || data?.logoUrl || '',
        themeColor: data?.branding?.themeColor || data?.themeColor || '',
        bgWallpaper: data?.branding?.bgWallpaper || data?.bgWallpaper || '',
        branding: data?.branding || {},
        property_type: data?.property_type || data?.propertyType || 'hotel',
        public_slug: data?.public_slug || data?.publicSlug || '',
        publicSlug: data?.public_slug || data?.publicSlug || '',
      });
      onStatus?.(status, data);
    },
    (err) => {
      console.error('[app] hotel status watch failed', err);
    },
  );
}

/** One-shot read used on route apply before the listener may have fired. */
async function fetchHotelStatus(hotelId) {
  if (!hotelId) return 'active';
  try {
    const snap = await getDoc(doc(db, 'Hotels', hotelId));
    if (!snap.exists()) return 'active';
    return snap.data()?.status || 'active';
  } catch (err) {
    console.error('[app] fetchHotelStatus failed', err);
    return 'active';
  }
}

function handleInactiveForCurrentUser(status) {
  const profile = getCurrentProfile();
  const inactive = isInactiveStatus(status);
  const meta = getHotelMeta();
  const label = meta?.name || getHotelId() || '';

  if (!inactive) {
    showDeactivatedGate(false);
    return false;
  }

  // Hotel admins / staff are hard-blocked from PMS.
  if (canAccessPropertyPms(profile)) {
    showShell('pms');
    setHotelChromeVisible(true);
    showDeactivatedGate(true, label);
    return true;
  }

  // Super Admin: keep master panel usable; don't open inactive PMS quietly.
  if (profile?.role === 'super_admin') {
    showDeactivatedGate(false);
    toast('This hotel is inactive. Reactivate it from Super Admin before opening PMS.', 'error');
    navigateTo('/super-admin');
    showShell('super-admin');
    setHotelChromeVisible(false);
    return true;
  }

  return false;
}

/** Super Admin Master Dashboard — no Hotels/{id} / property_type fetch. */
function enterSuperAdminDashboard() {
  stopHotelStatusWatch();
  showDeactivatedGate(false);
  startSuperAdminListeners();
  // Avoid navigateTo when already on this hash — it re-fires route listeners (loop).
  if (getRoute() !== '/super-admin') {
    navigateTo('/super-admin');
  }
  showShell('super-admin');
  setHotelChromeVisible(false);
}

async function applyRoute(route) {
  // Route guard: wait for Firebase Auth initial restore before any redirect/shell
  if (isAuthLoading()) {
    setAuthBootUi(true);
    return;
  }

  try {
    const profile = getCurrentProfile();

    if (!profile || needsBootstrap()) {
      stopHotelStatusWatch();
      showDeactivatedGate(false);
      showShell('login');
      return;
    }

    // ── Super Admin: Master Dashboard first; property checks only when impersonating ──
    if (profile.role === 'super_admin') {
      startSuperAdminListeners();

      const onMasterRoute = route === '/super-admin' || route === '/login';
      const wantsPropertyPms = route === '/pms' || String(route || '').startsWith('/pms');
      const canOpenProperty =
        wantsPropertyPms && hasHotelContext() && isImpersonating();

      // Default / login / no active impersonation → Master Dashboard (no property_type)
      if (onMasterRoute || !canOpenProperty) {
        stopHotelStatusWatch();
        showDeactivatedGate(false);
        if (!onMasterRoute && wantsPropertyPms && !canOpenProperty) {
          // Stale hotelId in localStorage without impersonation — don't block on property fetch
          try {
            clearHotelContext();
          } catch (_) {
            /* ignore */
          }
          toast('Select a hotel to open its PMS', 'error');
        }
        if (getRoute() !== '/super-admin') {
          navigateTo('/super-admin');
        }
        showShell('super-admin');
        setHotelChromeVisible(false);
        return;
      }

      // Explicit impersonation into a property PMS — status/property_type allowed here
      const hotelId = getHotelId();
      const status = await fetchHotelStatus(hotelId);
      cachedHotelStatus = status;
      startHotelStatusWatch(hotelId, (nextStatus) => {
        if (isInactiveStatus(nextStatus) && getRoute() !== '/super-admin') {
          handleInactiveForCurrentUser(nextStatus);
        } else if (!isInactiveStatus(nextStatus)) {
          showDeactivatedGate(false);
        }
      });

      if (handleInactiveForCurrentUser(status)) {
        return;
      }

      showShell('pms');
      setHotelChromeVisible(true);
      ensurePmsInited();
      showModule(resolveAllowedModule(getModuleFromRoute()));
      updateImpersonationBanner();
      return;
    }

    // ── Property admin / staff (RTDB staff_users role) ──
    if (canAccessPropertyPms(profile)) {
      stopSuperAdminListeners();
      if (!hasHotelContext() && profile?.hotelId) {
        setAssignedHotel(profile.hotelId, { name: profile.hotelId });
      }

      const hotelId = getHotelId() || profile?.hotelId;
      if (!hotelId) {
        showShell('login');
        toast('No hotel assigned to this account', 'error');
        return;
      }

      const status = await fetchHotelStatus(hotelId);
      cachedHotelStatus = status;
      startHotelStatusWatch(hotelId, (nextStatus) => {
        if (isInactiveStatus(nextStatus)) {
          handleInactiveForCurrentUser(nextStatus);
        } else {
          showDeactivatedGate(false);
          if (getRoute() !== '/login') {
            showShell('pms');
            setHotelChromeVisible(true);
            ensurePmsInited();
            showModule(resolveAllowedModule(getModuleFromRoute()));
          }
        }
      });

      if (handleInactiveForCurrentUser(status)) {
        return;
      }

      showDeactivatedGate(false);
      showShell('pms');
      setHotelChromeVisible(true);
      ensurePmsInited();
      applyRbacNavChrome();
      showModule(resolveAllowedModule(getModuleFromRoute()));
      document.getElementById('impersonation-bar')?.classList.add('hidden');
      document.getElementById('pms-impersonate-wrap')?.classList.add('hidden');
      return;
    }

    showShell('login');
    toast('Your account has no role. Ask a Super Admin to assign one.', 'error');
  } catch (err) {
    console.error('[app] applyRoute failed', err);
    try {
      const profile = getCurrentProfile();
      if (!profile) showShell('login');
      else if (profile.role === 'super_admin') enterSuperAdminDashboard();
      else showShell('pms');
    } catch (shellErr) {
      console.error('[app] fallback shell failed', shellErr);
      showShell('login');
    }
    toast(err?.message || 'Failed to open workspace', 'error');
  } finally {
    clearAuthBootUi();
  }
}

let pmsInited = false;
function ensurePmsInited() {
  if (pmsInited) return;
  pmsInited = true;
  setConnectionStatus('connecting');

  const steps = [
    ['navigation', initNavigation],
    ['audio', initAudio],
    ['orders', initOrders],
    ['guests', initGuests],
    ['alerts', initAlerts],
    ['menu', initMenu],
    ['housekeeping', initHousekeeping],
    ['emergency-contacts', initEmergencyContacts],
    ['daily-agenda', initDailyAgenda],
    ['announcement', initAnnouncement],
    ['concierge', initConcierge],
    ['analytics', initAnalytics],
    ['staff', initStaffManagement],
    ['pairing', initPairingClaim],
  ];

  for (const [name, init] of steps) {
    try {
      init();
    } catch (err) {
      console.error(`[app] PMS init failed (${name})`, err);
    }
  }
}

function updateImpersonationBanner() {
  const bar = document.getElementById('impersonation-bar');
  const nameEl = document.getElementById('impersonation-hotel-name');
  const wrap = document.getElementById('pms-impersonate-wrap');
  const meta = getHotelMeta();
  if (wrap) wrap.classList.toggle('hidden', !(isSuperAdmin() && hasHotelContext()));
  if (!bar) return;
  if (isSuperAdmin() && (isImpersonating() || getHotelId())) {
    bar.classList.remove('hidden');
    if (nameEl) nameEl.textContent = meta?.name || getHotelId();
  } else {
    bar.classList.add('hidden');
  }
}

function setupLoginForm() {
  const form = document.getElementById('login-form');
  form?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const email = document.getElementById('login-email')?.value?.trim();
    const password = document.getElementById('login-password')?.value || '';
    const btn = form.querySelector('button[type="submit"]');
    if (btn) {
      btn.disabled = true;
      btn.textContent = 'Signing in…';
    }
    try {
      const { profile } = await loginWithEmail(email, password);
      forceAuthReady();
      setAuthBootUi(false);

      if (profile?.role === 'super_admin') {
        // Role-first: Master Dashboard only — no property_type / Hotels/{id} gate
        enterSuperAdminDashboard();
        return;
      }

      if (canAccessPropertyPms(profile)) {
        const home = getDefaultModuleForRole(profile);
        console.info('[rbac] signed in as', getOperationalRole(profile), '→', home);
        navigateTo(`/pms/${home}`);
        await applyRoute(getRoute());
        return;
      }

      if (profile?.role === 'unknown') {
        toast('No users/{uid} profile yet — use Bootstrap Super Admin below.', 'error');
        showShell('login');
        return;
      }

      toast('Unknown role', 'error');
      showShell('login');
    } catch (err) {
      console.error(err);
      toast(err.message || 'Login failed', 'error');
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.textContent = 'Sign In';
      }
    }
  });

  document.getElementById('bootstrap-super-admin')?.addEventListener('click', async () => {
    try {
      await ensureSuperAdminProfile();
      toast('Super Admin profile saved');
      navigateTo('/super-admin');
      await applyRoute(getRoute());
    } catch (err) {
      toast(err.message || 'Bootstrap failed — sign in first', 'error');
    }
  });
}

function setupChromeActions() {
  document.querySelectorAll('[data-logout]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      stopHotelStatusWatch();
      showDeactivatedGate(false);
      await logout();
      navigateTo('/login');
      await applyRoute(getRoute());
    });
  });

  document.getElementById('back-to-super-admin')?.addEventListener('click', () => {
    stopHotelStatusWatch();
    showDeactivatedGate(false);
    navigateTo('/super-admin');
    applyRoute(getRoute());
  });

  document.getElementById('open-super-admin-link')?.addEventListener('click', () => {
    stopHotelStatusWatch();
    showDeactivatedGate(false);
    navigateTo('/super-admin');
    applyRoute(getRoute());
  });
}

document.addEventListener('DOMContentLoaded', () => {
  // Show boot loader immediately — hide login flash until Auth resolves
  setAuthBootUi(true);

  // Multi-tenant: *.hostity.in → bind Hotels/{slug} branding before Auth paints.
  // Localhost is unchanged (no forced default); use ?hotel=slug when testing.
  const hostSlug = extractTenantSlug(window.location.hostname);
  if (hostSlug || new URLSearchParams(window.location.search).get('hotel')) {
    bootstrapTenantFromHostname({ useDefaultOnLocal: false }).then((result) => {
      if (result.status === 'not_found') {
        toast(`Unknown hotel subdomain: ${result.slug}`, 'error');
      } else if (result.status === 'ready') {
        console.info('[tenant] bound from hostname', result.slug, result.hotel?.name);
      } else if (result.status === 'error') {
        console.error('[tenant] hostname bootstrap failed', result.error);
      }
    });
  }

  // Safety net: never leave the boot gate up indefinitely if Auth hangs.
  const bootWatchdog = window.setTimeout(() => {
    if (document.body.classList.contains('auth-booting') || isAuthLoading()) {
      console.warn('[app] auth boot watchdog fired — forcing login');
      forceAuthReady();
      setAuthBootUi(false);
      stopHotelStatusWatch();
      showDeactivatedGate(false);
      navigateTo('/login');
      showShell('login');
      toast('Session restore timed out. Please sign in.', 'error');
    }
  }, 5000);

  try {
    initRouter();
    setupLoginForm();
    setupChromeActions();
    initSuperAdmin();
  } catch (err) {
    console.error('[app] chrome init failed', err);
    window.clearTimeout(bootWatchdog);
    forceAuthReady();
    setAuthBootUi(false);
    navigateTo('/login');
    showShell('login');
    toast('Failed to start admin panel', 'error');
    return;
  }

  onRouteChange((route) => {
    if (isAuthLoading()) {
      setAuthBootUi(true);
      return;
    }
    applyRoute(route).catch((err) => {
      console.error('[app] route change failed', err);
      clearAuthBootUi();
    });
  });

  // When Super Admin switches hotel while already on PMS, re-run the status gate.
  // Skip the immediate onHotelChange invocation (auth/route handlers own first paint).
  let hotelChangeReady = false;
  onHotelChange((id) => {
    if (!hotelChangeReady || isAuthLoading()) return;
    if (!id || !getCurrentProfile()) {
      stopHotelStatusWatch();
      showDeactivatedGate(false);
      return;
    }
    if (getRoute() === '/login' || getRoute() === '/super-admin') return;
    applyRoute(getRoute()).catch((err) => {
      console.error('[app] hotel-change route failed', err);
      clearAuthBootUi();
    });
  });
  hotelChangeReady = true;

  initAuth((user, profile) => {
    try {
      window.clearTimeout(bootWatchdog);
      // Always drop the boot gate first so login is visible even if routing throws.
      forceAuthReady();
      setAuthBootUi(false);

      if (!user) {
        stopHotelStatusWatch();
        showDeactivatedGate(false);
        navigateTo('/login');
        showShell('login');
        return;
      }

      // Super Admin session restore → Master Dashboard (skip property_type entirely)
      if (profile?.role === 'super_admin') {
        const route = getRoute();
        const onPms = route === '/pms' || String(location.hash || '').includes('/pms');
        if (!onPms || !isImpersonating()) {
          try {
            if (!isImpersonating()) clearHotelContext();
          } catch (_) {
            /* ignore */
          }
          enterSuperAdminDashboard();
          return;
        }
        applyRoute(route).catch((err) => {
          console.error('[app] super_admin PMS route failed', err);
          enterSuperAdminDashboard();
        });
        return;
      }

      // Property admin (or other): preserve deep-link; default away from login
      if (!location.hash || location.hash === '#/login') {
        navigateTo('/pms');
      }
      applyRoute(getRoute()).catch((err) => {
        console.error('[app] initial route failed', err);
        clearAuthBootUi();
      });
    } catch (err) {
      console.error('[app] auth ready handler failed', err);
      forceAuthReady();
      setAuthBootUi(false);
      if (profile?.role === 'super_admin') {
        enterSuperAdminDashboard();
      } else {
        navigateTo('/login');
        showShell('login');
        toast(err?.message || 'Failed to restore session', 'error');
      }
    } finally {
      forceAuthReady();
      clearAuthBootUi();
    }
  });
});
