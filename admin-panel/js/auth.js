import {
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut,
  createUserWithEmailAndPassword,
  updatePassword,
  EmailAuthProvider,
  reauthenticateWithCredential,
  sendPasswordResetEmail,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-auth.js';
import {
  doc,
  getDoc,
  setDoc,
  serverTimestamp,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import { db, auth, secondaryAuth, rtdb } from './firebase-config.js';
import {
  TenantManager,
  setAssignedHotel,
  clearHotelContext,
  getHotelId,
} from './tenant-context.js';
import { ref, get, set } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-database.js';
import { normalizeStaffRole } from './rbac-roles.js';

export { auth };

let currentUser = null;
let currentProfile = null;
/** True until the first onAuthStateChanged settles (signed-in or signed-out). */
let authLoading = true;
const authListeners = new Set();

const PROFILE_LOAD_TIMEOUT_MS = 12000;
/** If Firebase never delivers the first auth event, unlock as signed-out. */
const AUTH_EVENT_FAILSAFE_MS = 4000;

const AUTH_DENIED_MESSAGE = 'Invalid email or password';
const AUTH_UNAUTHORIZED_MESSAGE =
  'This account is not authorized. Contact your administrator.';

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

/** True when profile has a known admin / staff role that may enter the panel. */
export function hasAuthorizedProfile(profile = currentProfile) {
  if (!profile) return false;
  if (profile.role === 'super_admin') return true;
  if (profile.role === 'hotel_admin') return true;
  if (normalizeStaffRole(profile.staffRole) && profile.hotelId) return true;
  return false;
}

/**
 * Map Firebase Auth errors to a safe UI message.
 * Never leak whether the email exists.
 */
export function formatAuthError(err) {
  const code = String(err?.code || '');
  if (
    code === 'auth/invalid-credential' ||
    code === 'auth/wrong-password' ||
    code === 'auth/user-not-found' ||
    code === 'auth/invalid-email' ||
    code === 'auth/invalid-login-credentials' ||
    code === 'auth/missing-password' ||
    code === 'auth/user-disabled'
  ) {
    return AUTH_DENIED_MESSAGE;
  }
  if (code === 'auth/too-many-requests') {
    return 'Too many attempts. Please try again later.';
  }
  if (code === 'auth/network-request-failed') {
    return 'Network error. Check your connection and try again.';
  }
  if (code === 'auth/requires-recent-login') {
    return 'Please re-enter your current password and try again.';
  }
  if (code === 'auth/weak-password') {
    return 'Password must be at least 6 characters.';
  }
  if (err?.message === AUTH_UNAUTHORIZED_MESSAGE) {
    return AUTH_UNAUTHORIZED_MESSAGE;
  }
  return AUTH_DENIED_MESSAGE;
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
      currentProfile = null;
      return null;
    }
    const data = snap.data() || {};
    currentProfile = {
      role: data.role || 'hotel_admin',
      hotelId: data.hotelId || '',
      email: data.email || user.email || '',
      displayName: data.displayName || '',
      uid: user.uid,
      staffRole: normalizeStaffRole(data.staffRole || data.operationalRole) || null,
    };
    return currentProfile;
  } catch (err) {
    console.error('[auth] loadUserProfile failed', err);
    // Do NOT invent an "unknown" role that could unlock elevation UI.
    currentProfile = null;
    return null;
  }
}

function withTimeout(promise, ms, label) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(`${label} timed out after ${ms}ms`)), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

/**
 * Load staff_users/{uid} from Realtime Database (operational RBAC role).
 * @returns {Promise<{role:string,hotelId?:string,displayName?:string,email?:string}|null>}
 */
async function loadStaffUserRecord(uid) {
  if (!uid) return null;
  try {
    const snap = await get(ref(rtdb, `staff_users/${uid}`));
    if (!snap.exists()) return null;
    const data = snap.val() || {};
    return {
      role: normalizeStaffRole(data.role) || null,
      hotelId: data.hotelId || data.hotel_id || '',
      displayName: data.displayName || data.name || '',
      email: data.email || '',
      raw: data,
    };
  } catch (err) {
    console.error('[auth] loadStaffUserRecord failed', err);
    return null;
  }
}

/** Merge RTDB staff role into the in-memory profile used by RBAC. */
async function attachStaffRole(user) {
  if (!user?.uid) return currentProfile;
  const staff = await loadStaffUserRecord(user.uid);
  if (!staff) {
    if (currentProfile?.role === 'hotel_admin' && !currentProfile.staffRole) {
      currentProfile.staffRole = 'admin';
    }
    return currentProfile;
  }

  if (!currentProfile) {
    // Staff-only account: RTDB role + hotel is enough for PMS (no Firestore users doc).
    if (staff.role && staff.hotelId) {
      currentProfile = {
        role: 'hotel_admin',
        hotelId: staff.hotelId,
        email: staff.email || user.email || '',
        displayName: staff.displayName || '',
        uid: user.uid,
        staffRole: staff.role,
      };
    }
    return currentProfile;
  }

  if (staff.role) currentProfile.staffRole = staff.role;
  if (staff.hotelId && !currentProfile.hotelId) {
    currentProfile.hotelId = staff.hotelId;
  }
  if (staff.displayName && !currentProfile.displayName) {
    currentProfile.displayName = staff.displayName;
  }

  if (
    staff.role &&
    (!currentProfile.role || currentProfile.role === 'unknown') &&
    (staff.hotelId || currentProfile.hotelId)
  ) {
    currentProfile.role = 'hotel_admin';
  }

  return currentProfile;
}

/** Authenticated session bind. Super Admin skips all property binding. */
async function loadAuthenticatedSession(user) {
  await loadUserProfile(user);
  await attachStaffRole(user);

  if (currentProfile?.role === 'super_admin') {
    return currentProfile;
  }

  if (
    (currentProfile?.role === 'hotel_admin' || normalizeStaffRole(currentProfile?.staffRole)) &&
    currentProfile?.hotelId
  ) {
    setAssignedHotel(currentProfile.hotelId, { name: currentProfile.hotelId });
  }
  return currentProfile;
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

    // User exists — cancel signed-out failsafe before any profile await
    clearTimeout(failsafeTimer);

    // ── 2) Signed in: load role; unauthorized Auth users are signed out ─
    try {
      await withTimeout(
        loadAuthenticatedSession(user),
        PROFILE_LOAD_TIMEOUT_MS,
        'Auth profile load',
      );
    } catch (err) {
      console.error('[auth] profile load failed', err);
      currentProfile = null;
    }

    if (!hasAuthorizedProfile(currentProfile)) {
      console.warn('[auth] signed-in user has no authorized profile — forcing sign-out');
      try {
        await signOut(auth);
      } catch (signOutErr) {
        console.error('[auth] forced sign-out failed', signOutErr);
      }
      currentUser = null;
      currentProfile = null;
      try {
        clearHotelContext();
      } catch (_) {
        /* ignore */
      }
      firstEventDone = true;
      resolveAuthGate(null, null, { initial: wasInitialLoad }, onReady);
      return;
    }

    firstEventDone = true;
    resolveAuthGate(user, currentProfile, { initial: wasInitialLoad }, onReady);
  });
}

export async function loginWithEmail(email, password) {
  const trimmedEmail = String(email || '').trim();
  const trimmedPassword = String(password || '');
  if (!trimmedEmail || !trimmedPassword) {
    const err = new Error(AUTH_DENIED_MESSAGE);
    err.code = 'auth/invalid-credential';
    throw err;
  }

  let cred;
  try {
    cred = await signInWithEmailAndPassword(auth, trimmedEmail, trimmedPassword);
  } catch (err) {
    const wrapped = new Error(formatAuthError(err));
    wrapped.code = err?.code || 'auth/invalid-credential';
    throw wrapped;
  }

  currentUser = cred.user;
  try {
    await withTimeout(
      loadAuthenticatedSession(cred.user),
      PROFILE_LOAD_TIMEOUT_MS,
      'Login profile load',
    );
  } catch (err) {
    console.error('[auth] login profile load failed', err);
    currentProfile = null;
  }

  if (!hasAuthorizedProfile(currentProfile)) {
    try {
      await signOut(auth);
    } catch (_) {
      /* ignore */
    }
    currentUser = null;
    currentProfile = null;
    try {
      clearHotelContext();
    } catch (_) {
      /* ignore */
    }
    authLoading = false;
    notifyAuth();
    const denied = new Error(AUTH_UNAUTHORIZED_MESSAGE);
    denied.code = 'auth/unauthorized-profile';
    throw denied;
  }

  if (currentProfile?.role === 'super_admin') {
    try {
      clearHotelContext();
    } catch (err) {
      console.error('[auth] clearHotelContext for super_admin failed', err);
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
export async function createHotelAdminAccount({
  email,
  password,
  hotelId,
  displayName,
  staffRole = 'admin',
}) {
  const cred = await createUserWithEmailAndPassword(secondaryAuth, email.trim(), password);
  const uid = cred.user.uid;
  const opsRole = normalizeStaffRole(staffRole) || 'admin';
  await setDoc(doc(db, 'users', uid), {
    role: 'hotel_admin',
    hotelId,
    email: email.trim(),
    displayName: displayName || '',
    staffRole: opsRole,
    createdAt: serverTimestamp(),
  });
  try {
    await set(ref(rtdb, `staff_users/${uid}`), {
      role: opsRole,
      hotelId,
      email: email.trim(),
      displayName: displayName || '',
      createdAt: Date.now(),
    });
  } catch (err) {
    console.warn('[auth] staff_users mirror failed (Firestore admin still created)', err);
  }
  await signOut(secondaryAuth);
  return uid;
}

/**
 * Change the signed-in user's password (Firebase Client SDK).
 * Re-authenticates with [currentPassword] first.
 */
export async function changeOwnPassword(currentPassword, newPassword) {
  const user = auth.currentUser;
  if (!user?.email) {
    throw new Error('You must be signed in to change your password');
  }
  const next = String(newPassword || '');
  if (next.length < 6) {
    const err = new Error('Password must be at least 6 characters.');
    err.code = 'auth/weak-password';
    throw err;
  }
  try {
    const credential = EmailAuthProvider.credential(user.email, String(currentPassword || ''));
    await reauthenticateWithCredential(user, credential);
    await updatePassword(user, next);
  } catch (err) {
    const wrapped = new Error(formatAuthError(err));
    wrapped.code = err?.code || 'auth/invalid-credential';
    throw wrapped;
  }
}

/**
 * Send a Firebase password-reset email for another staff account.
 * Client SDK cannot set other users' passwords without Admin SDK.
 */
export async function sendStaffPasswordReset(email) {
  const target = String(email || '').trim();
  if (!target) throw new Error('Staff email is required');
  if (!hasAuthorizedProfile(currentProfile)) {
    throw new Error('Not authorized');
  }
  try {
    await sendPasswordResetEmail(auth, target);
  } catch (err) {
    console.error('[auth] sendPasswordResetEmail failed', err?.code || err);
    const wrapped = new Error(
      err?.code === 'auth/invalid-email'
        ? 'Invalid email address'
        : 'Could not send reset email. Try again or check the address.',
    );
    wrapped.code = err?.code || 'auth/reset-failed';
    throw wrapped;
  }
}

export function getEffectiveHotelId() {
  return getHotelId() || currentProfile?.hotelId || '';
}

export { TenantManager };
