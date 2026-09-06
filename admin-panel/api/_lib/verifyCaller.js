/**
 * Verify Firebase ID token and load Firestore users/{uid} profile.
 */

const { getAdmin } = require('./firebaseAdmin');
const { getBearerToken } = require('./http');

async function verifyIdToken(req) {
  const token = getBearerToken(req);
  if (!token) {
    const err = new Error('Missing Authorization Bearer token');
    err.statusCode = 401;
    throw err;
  }
  const admin = getAdmin();
  try {
    return await admin.auth().verifyIdToken(token, true);
  } catch (e) {
    const err = new Error('Invalid or expired auth token');
    err.statusCode = 401;
    throw err;
  }
}

async function loadUserProfile(uid) {
  const admin = getAdmin();
  const snap = await admin.firestore().collection('users').doc(uid).get();
  if (!snap.exists) return null;
  return { uid, ...(snap.data() || {}) };
}

async function loadStaffRecord(uid) {
  const admin = getAdmin();
  const snap = await admin.database().ref(`staff_users/${uid}`).get();
  if (!snap.exists()) return null;
  return snap.val() || {};
}

/**
 * Require Super Admin, or hotel_admin for the same hotel as target staff.
 */
async function requirePasswordManager(req, { targetUid } = {}) {
  const decoded = await verifyIdToken(req);
  const profile = await loadUserProfile(decoded.uid);
  if (!profile) {
    const err = new Error('Not authorized');
    err.statusCode = 403;
    throw err;
  }

  if (profile.role === 'super_admin') {
    return { decoded, profile, isSuperAdmin: true };
  }

  if (profile.role !== 'hotel_admin' || !profile.hotelId) {
    const err = new Error('Not authorized — Super Admin or Hotel Admin required');
    err.statusCode = 403;
    throw err;
  }

  if (targetUid) {
    const staff = await loadStaffRecord(targetUid);
    const staffHotel = staff?.hotelId || staff?.hotel_id || '';
    if (staffHotel && staffHotel !== profile.hotelId) {
      const err = new Error('Not authorized to manage staff outside your hotel');
      err.statusCode = 403;
      throw err;
    }
  }

  return { decoded, profile, isSuperAdmin: false };
}

/**
 * Super Admin (any hotel) or Hotel Admin for [hotelId] only.
 */
async function requireHotelAdminOrSuper(req, hotelId) {
  return requireHotelAccess(req, hotelId, { receptionOk: false });
}

/**
 * Who may manage TV pairing for a hotel:
 * - Firestore super_admin
 * - Firestore hotel_admin for that hotelId
 * - RTDB staff_users/{uid} with matching hotelId and role admin|reception|hotel_admin
 *
 * @param {{ receptionOk?: boolean }} [opts]
 */
async function requireHotelAccess(req, hotelId, opts = {}) {
  const receptionOk = opts.receptionOk !== false;
  const decoded = await verifyIdToken(req);
  const target = String(hotelId || '').trim();
  const sameHotel = (a, b) =>
    String(a || '').trim().toLowerCase() === String(b || '').trim().toLowerCase();
  if (!target) {
    const err = new Error('hotelId is required');
    err.statusCode = 400;
    throw err;
  }

  const profile = await loadUserProfile(decoded.uid);
  if (profile?.role === 'super_admin') {
    return {
      decoded,
      profile: { ...profile, uid: decoded.uid },
      isSuperAdmin: true,
    };
  }

  if (
    profile?.role === 'hotel_admin' &&
    sameHotel(profile.hotelId || profile.hotel_id, target)
  ) {
    return {
      decoded,
      profile: { ...profile, uid: decoded.uid },
      isSuperAdmin: false,
    };
  }

  // Fallback: RTDB staff_users (some reception accounts have no Firestore users doc).
  const staff = await loadStaffRecord(decoded.uid);
  const staffHotel = String(staff?.hotelId || staff?.hotel_id || '').trim();
  const staffRole = String(staff?.role || '')
    .trim()
    .toLowerCase();
  const allowedStaff = receptionOk
    ? new Set(['admin', 'reception', 'hotel_admin', 'manager'])
    : new Set(['admin', 'hotel_admin', 'manager']);

  if (sameHotel(staffHotel, target) && (!staffRole || allowedStaff.has(staffRole))) {
    return {
      decoded,
      profile: {
        uid: decoded.uid,
        role: 'hotel_admin',
        hotelId: target,
        staffRole: staffRole || 'reception',
        email: staff?.email || profile?.email || '',
      },
      isSuperAdmin: false,
    };
  }

  // Custom claims hotelId (paired staff / minted tokens).
  if (sameHotel(decoded.hotelId, target)) {
    return {
      decoded,
      profile: {
        uid: decoded.uid,
        role: profile?.role || 'hotel_admin',
        hotelId: target,
      },
      isSuperAdmin: false,
    };
  }

  const err = new Error(
    'Permission denied — your account is not allowed to manage TVs for this hotel',
  );
  err.statusCode = 403;
  throw err;
}

module.exports = {
  verifyIdToken,
  loadUserProfile,
  loadStaffRecord,
  requirePasswordManager,
  requireHotelAdminOrSuper,
  requireHotelAccess,
};
