import { initAudio, setConnectionStatus, toast } from './utils.js';
import { initOrders } from './orders.js';
import { initAlerts } from './alerts.js';
import { initMenu } from './menu.js';
import { initGuests } from './guests.js';
import { initHousekeeping, initConcierge } from './requests.js';
import { initAnalytics } from './analytics.js';
import { initNavigation, showModule, setHotelChromeVisible } from './navigation.js';
import {
  initAuth,
  loginWithEmail,
  logout,
  isSuperAdmin,
  getCurrentProfile,
  ensureSuperAdminProfile,
  needsBootstrap,
} from './auth.js';
import {
  getHotelId,
  hasHotelContext,
  setAssignedHotel,
  getHotelMeta,
  isImpersonating,
  updateHotelMeta,
  onHotelChange,
} from './tenant-context.js';
import { initSuperAdmin, startSuperAdminListeners, stopSuperAdminListeners } from './super-admin.js';
import { initRouter, onRouteChange, navigateTo, getRoute, getModuleFromRoute } from './router.js';
import { db } from './firebase-config.js';
import { doc, getDoc, onSnapshot } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import './paths.js';

/** @type {(() => void) | null} */
let hotelStatusUnsub = null;
/** @type {string} */
let cachedHotelStatus = 'active';

function showShell(which) {
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
        name: data.name || hotelId,
        status,
        logoUrl: data.branding?.logoUrl || data.logoUrl || '',
        themeColor: data.branding?.themeColor || data.themeColor || '',
        bgWallpaper: data.branding?.bgWallpaper || data.bgWallpaper || '',
        branding: data.branding || {},
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

  // Hotel admins are hard-blocked from PMS.
  if (profile?.role === 'hotel_admin') {
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

async function applyRoute(route) {
  const profile = getCurrentProfile();

  if (!profile || needsBootstrap()) {
    stopHotelStatusWatch();
    showDeactivatedGate(false);
    showShell('login');
    return;
  }

  if (profile.role === 'super_admin') {
    // Keep hotels registry live so both Super Admin + PMS navbar dropdowns stay filled
    startSuperAdminListeners();

    if (route === '/super-admin' || route === '/login') {
      stopHotelStatusWatch();
      showDeactivatedGate(false);
      showShell('super-admin');
      setHotelChromeVisible(false);
      return;
    }

    if (!hasHotelContext()) {
      toast('Select a hotel to open its PMS', 'error');
      navigateTo('/super-admin');
      showShell('super-admin');
      return;
    }

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
    showModule(getModuleFromRoute());
    updateImpersonationBanner();
    return;
  }

  if (profile.role === 'hotel_admin') {
    stopSuperAdminListeners();
    if (!hasHotelContext() && profile.hotelId) {
      setAssignedHotel(profile.hotelId, { name: profile.hotelId });
    }

    const hotelId = getHotelId() || profile.hotelId;
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
        // If they were locked and hotel is reactivated while still on PMS hash, restore UI
        if (getRoute() !== '/login') {
          showShell('pms');
          setHotelChromeVisible(true);
          ensurePmsInited();
          showModule(getModuleFromRoute());
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
    showModule(getModuleFromRoute());
    document.getElementById('impersonation-bar')?.classList.add('hidden');
    document.getElementById('pms-impersonate-wrap')?.classList.add('hidden');
    return;
  }

  showShell('login');
  toast('Your account has no role. Ask a Super Admin to assign one.', 'error');
}

let pmsInited = false;
function ensurePmsInited() {
  if (pmsInited) return;
  pmsInited = true;
  setConnectionStatus('connecting');
  initNavigation();
  initAudio();
  initOrders();
  initGuests();
  initAlerts();
  initMenu();
  initHousekeeping();
  initConcierge();
  initAnalytics();
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
      if (profile?.role === 'super_admin') {
        navigateTo('/super-admin');
      } else if (profile?.role === 'hotel_admin') {
        navigateTo('/pms');
      } else if (profile?.role === 'unknown') {
        toast('No users/{uid} profile yet — use Bootstrap Super Admin below.', 'error');
      } else {
        toast('Unknown role', 'error');
      }
      await applyRoute(getRoute());
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
  initRouter();
  setupLoginForm();
  setupChromeActions();
  initSuperAdmin();

  onRouteChange((route) => {
    applyRoute(route);
  });

  // When Super Admin switches hotel while already on PMS, re-run the status gate.
  // Skip the immediate onHotelChange invocation (auth/route handlers own first paint).
  let hotelChangeReady = false;
  onHotelChange((id) => {
    if (!hotelChangeReady) return;
    if (!id || !getCurrentProfile()) {
      stopHotelStatusWatch();
      showDeactivatedGate(false);
      return;
    }
    if (getRoute() === '/login' || getRoute() === '/super-admin') return;
    applyRoute(getRoute());
  });
  hotelChangeReady = true;

  initAuth((user, profile) => {
    if (!user) {
      stopHotelStatusWatch();
      showDeactivatedGate(false);
      showShell('login');
      navigateTo('/login');
      return;
    }
    if (!location.hash || location.hash === '#/login') {
      if (profile?.role === 'super_admin') navigateTo('/super-admin');
      else navigateTo('/pms');
    }
    applyRoute(getRoute());
  });
});
