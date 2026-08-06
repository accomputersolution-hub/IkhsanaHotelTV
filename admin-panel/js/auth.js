import {
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut,
  createUserWithEmailAndPassword,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-auth.js';
import {
  doc,
  getDoc,
  setDoc,
  serverTimestamp,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import { db, auth, secondaryAuth } from './firebase-config.js';
import {
  TenantManager,
  setAssignedHotel,
  clearHotelContext,
  getHotelId,
} from './tenant-context.js';

export { auth };

let currentUser = null;
let currentProfile = null;
/** True until the first onAuthStateChanged settles (signed-in or signed-out). */
let authLoading = true;
const authListeners = new Set();

const PROFILE_LOAD_TIMEOUT_MS = 12000;
/** If Firebase never delivers the first auth event, unlock as signed-out. */
const AUTH_EVENT_FAILSAFE_MS = 4000;

export function getCurrentUser() {
  return currentUser;
}

export function getCurrentProfile() {
  return currentProfile;
}

/** Route guards must wait until this is false. */
export function isAuthLoading() {
  return authLoading;
}

/** Last-resort unlock if session restore hangs (boot watchdog). */
export function forceAuthReady() {
  authLoading = false;
}

export function isSuperAdmin() {
  return currentProfile?.role === 'super_admin';
}

export function isHotelAdmin() {
  return currentProfile?.role === 'hotel_admin';
}

export function needsBootstrap() {
  return Boolean(currentUser) && (!currentProfile || currentProfile.role === 'unknown');
}

/**
 * Subscribe to auth + profile changes.
 * Does NOT invoke immediately while still loading — first paint waits for Firebase.
 */
export function onAuthProfileChange(fn) {
  authListeners.add(fn);
  if (!authLoading) {
    fn(currentUser, currentProfile);
  }
  return () => authListeners.delete(fn);
}

function notifyAuth() {
  authListeners.forEach((fn) => {
    try {
      fn(currentUser, currentProfile);
    } catch (err) {
      console.error('[auth] listener error', err);
    }
  });
  updateBootstrapGateUi();
}

function updateBootstrapGateUi() {
  const gate = document.getElementById('bootstrap-gate');
  const form = document.getElementById('login-form');
  if (!gate) return;
  const show = needsBootstrap();
  gate.classList.toggle('hidden', !show);
  if (form) form.classList.toggle('opacity-60', show);
}

/**
 * Load users/{uid} only when a Firebase user exists.
 * Must never be called with a null user (no blind Firestore fetch).
 */
async function loadUserProfile(user) {
  if (!user?.uid) {
    currentProfile = null;
    return null;
  }

  try {
    const snap = await getDoc(doc(db, 'users', user.uid));
    if (!snap.exists()) {
      currentProfile = {
        role: 'unknown',
        hotelId: '',
        email: user.email || '',
        uid: user.uid,
      };
      return currentProfile;
    }
    const data = snap.data() || {};
    currentProfile = {
      role: data.role || 'hotel_admin',
      hotelId: data.hotelId || '',
      email: data.email || user.email || '',
      displayName: data.displayName || '',
      uid: user.uid,
    };
    return currentProfile;
  } catch (err) {
    console.error('[auth] loadUserProfile failed', err);
    currentProfile = {
      role: 'unknown',
      hotelId: '',
      email: user.email || '',
      uid: user.uid,
    };
    return currentProfile;
  }
}

function withTimeout(promise, ms, label) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(`${label} timed out after ${ms}ms`)), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

/** Authenticated-only session bind (profile + hotel). Never runs for null user. */
async function loadAuthenticatedSession(user) {
  await loadUserProfile(user);
  if (currentProfile?.role === 'hotel_admin' && currentProfile?.hotelId) {
    setAssignedHotel(currentProfile.hotelId, { name: currentProfile.hotelId });
  }
}

/**
 * Resolve the boot gate. Always sets authLoading=false.
 * @param {import('firebase/auth').User | null} user
 * @param {object | null} profile
 * @param {{ initial?: boolean }} [meta]
 * @param {(user, profile, meta) => void} [onReady]
 */
function resolveAuthGate(user, profile, meta, onReady) {
  authLoading = false;
  try {
    notifyAuth();
  } catch (notifyErr) {
    console.error('[auth] notify failed', notifyErr);
  }
  try {
    onReady?.(user, profile, meta);
  } catch (readyErr) {
    console.error('[auth] onReady failed', readyErr);
  }
}

/**
 * Bind Firebase Auth.
 *
 * Unauthenticated (user === null): resolve immediately — no Firestore reads.
 * Authenticated: load users/{uid} (try/catch + timeout), then resolve.
 * Loading ALWAYS becomes false in both paths.
 */
export function initAuth(onReady) {
  let firstEventDone = false;

  const failsafeTimer = setTimeout(() => {
    if (firstEventDone) return;
    console.warn('[auth] No onAuthStateChanged event — treating as signed out');
    firstEventDone = true;
    currentUser = null;
    currentProfile = null;
    try {
      clearHotelContext();
    } catch (err) {
      console.error('[auth] clearHotelContext failed', err);
    }
    resolveAuthGate(null, null, { initial: true }, onReady);
  }, AUTH_EVENT_FAILSAFE_MS);

  return onAuthStateChanged(auth, async (user) => {
    const wasInitialLoad = !firstEventDone;
    currentUser = user;

    // ── 1) Signed out: unlock immediately, never touch Firestore ──────────
    if (!user) {
      firstEventDone = true;
      clearTimeout(failsafeTimer);
      currentProfile = null;
      try {
        clearHotelContext();
      } catch (err) {
        console.error('[auth] clearHotelContext failed', err);
      }
      resolveAuthGate(null, null, { initial: wasInitialLoad }, onReady);
      return;
    }

    // ── 2) Signed in: fetch profile (never block the boot gate forever) ───
    try {
      await withTimeout(
        loadAuthenticatedSession(user),
        PROFILE_LOAD_TIMEOUT_MS,
        'Auth profile load',
      );
    } catch (err) {
      console.error('[auth] profile load failed', err);
      if (!currentProfile) {
        currentProfile = {
          role: 'unknown',
          hotelId: '',
          email: user.email || '',
          uid: user.uid,
        };
      }
    } finally {
      firstEventDone = true;
      clearTimeout(failsafeTimer);
      resolveAuthGate(user, currentProfile, { initial: wasInitialLoad }, onReady);
    }
  });
}

export async function loginWithEmail(email, password) {
  const cred = await signInWithEmailAndPassword(auth, email.trim(), password);
  currentUser = cred.user;
  try {
    await withTimeout(
      loadAuthenticatedSession(cred.user),
      PROFILE_LOAD_TIMEOUT_MS,
      'Login profile load',
    );
  } catch (err) {
    console.error('[auth] login profile load failed', err);
    if (!currentProfile) {
      currentProfile = {
        role: 'unknown',
        hotelId: '',
        email: cred.user.email || '',
        uid: cred.user.uid,
      };
    }
  }
  authLoading = false;
  notifyAuth();
  return { user: cred.user, profile: currentProfile };
}

export async function logout() {
  try {
    clearHotelContext();
  } catch (err) {
    console.error('[auth] clearHotelContext on logout failed', err);
  }
  await signOut(auth);
  currentUser = null;
  currentProfile = null;
  authLoading = false;
  notifyAuth();
}

/**
 * Creates Auth user + users/{uid} with role hotel_admin + hotelId
 * without signing out the current Super Admin (uses secondaryAuth).
 */
export async function createHotelAdminAccount({ email, password, hotelId, displayName }) {
  const cred = await createUserWithEmailAndPassword(secondaryAuth, email.trim(), password);
  const uid = cred.user.uid;
  await setDoc(doc(db, 'users', uid), {
    role: 'hotel_admin',
    hotelId,
    email: email.trim(),
    displayName: displayName || '',
    createdAt: serverTimestamp(),
  });
  await signOut(secondaryAuth);
  return uid;
}

/** First-login bootstrap: initialize current user as super_admin */
export async function ensureSuperAdminProfile(displayName = 'Super Admin') {
  const user = auth.currentUser;
  if (!user) throw new Error('Sign in first, then bootstrap');
  await setDoc(
    doc(db, 'users', user.uid),
    {
      role: 'super_admin',
      hotelId: '',
      email: user.email || '',
      displayName,
      updatedAt: serverTimestamp(),
    },
    { merge: true },
  );
  await loadUserProfile(user);
  notifyAuth();
  return currentProfile;
}

export function getEffectiveHotelId() {
  return getHotelId() || currentProfile?.hotelId || '';
}

export { TenantManager };
