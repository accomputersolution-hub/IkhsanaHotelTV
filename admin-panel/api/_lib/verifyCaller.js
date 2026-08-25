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

module.exports = {
  verifyIdToken,
  loadUserProfile,
  loadStaffRecord,
  requirePasswordManager,
};
