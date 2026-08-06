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
/** True until the first onAuthStateChanged (+ profile load) completes. */
let authLoading = true;
const authListeners = new Set();

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

async function loadUserProfile(user) {
  if (!user) {
    currentProfile = null;
    return null;
  }
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
  const data = snap.data();
  currentProfile = {
    role: data.role || 'hotel_admin',
    hotelId: data.hotelId || '',
    email: data.email || user.email || '',
    displayName: data.displayName || '',
    uid: user.uid,
  };
  return currentProfile;
}

const PROFILE_LOAD_TIMEOUT_MS = 15000;

function withTimeout(promise, ms, label) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(`${label} timed out after ${ms}ms`)), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

async function restoreSession(user) {
  await loadUserProfile(user);
  if (user && currentProfile?.role === 'hotel_admin' && currentProfile?.hotelId) {
    setAssignedHotel(currentProfile.hotelId, { name: currentProfile.hotelId });
  }
  if (!user) {
    clearHotelContext();
  }
}

/**
 * Bind Firebase Auth. `loading` stays true until the first callback finishes
 * (session restore + Firestore profile), then flips to false permanently for
 * subsequent auth events (login/logout still notify listeners).
 *
 * Always clears `authLoading` in `finally` so a failed/hung profile fetch
 * cannot leave the UI stuck on “Restoring session”.
 */
export function initAuth(onReady) {
  return onAuthStateChanged(auth, async (user) => {
    currentUser = user;
    const wasInitialLoad = authLoading;

    try {
      await withTimeout(restoreSession(user), PROFILE_LOAD_TIMEOUT_MS, 'Auth profile load');
    } catch (err) {
      console.error('[auth] profile load failed', err);
      if (!user) {
        currentProfile = null;
        try {
          clearHotelContext();
        } catch (clearErr) {
          console.error('[auth] clearHotelContext failed', clearErr);
        }
      } else if (!currentProfile) {
        // Keep a safe stub so route guards can still leave the boot screen.
        currentProfile = {
          role: 'unknown',
          hotelId: '',
          email: user.email || '',
          uid: user.uid,
        };
      }
    } finally {
      authLoading = false;
      try {
        notifyAuth();
      } catch (notifyErr) {
        console.error('[auth] notify failed', notifyErr);
      }
      try {
        onReady?.(user, currentProfile, { initial: wasInitialLoad });
      } catch (readyErr) {
        console.error('[auth] onReady failed', readyErr);
      }
    }
  });
}

export async function loginWithEmail(email, password) {
  const cred = await signInWithEmailAndPassword(auth, email.trim(), password);
  await loadUserProfile(cred.user);
  if (currentProfile?.role === 'hotel_admin' && currentProfile.hotelId) {
    setAssignedHotel(currentProfile.hotelId, { name: currentProfile.hotelId });
  }
  authLoading = false;
  notifyAuth();
  return { user: cred.user, profile: currentProfile };
}

export async function logout() {
  clearHotelContext();
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
