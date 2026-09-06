/**
 * POST /api/tv/unpair
 * Body: { hotelId, roomNumber }
 * Auth: Bearer Firebase ID token (Super Admin, Hotel Admin, or reception for that hotel)
 *
 * Clears TV pairing in Firestore + signals RTDB so the device returns to the
 * Hotel ID / Room pairing screen. Uses Admin SDK to bypass client rule denials.
 */

const adminSdk = require('firebase-admin');
const { getAdmin } = require('../_lib/firebaseAdmin');
const { setCors, sendJson, readJsonBody } = require('../_lib/http');
const { requireHotelAccess } = require('../_lib/verifyCaller');

function normalizeRoom(raw) {
  return String(raw || '')
    .trim()
    .replace(/\s+/g, ' ');
}

module.exports = async function handler(req, res) {
  setCors(res);
  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    res.end();
    return;
  }
  if (req.method !== 'POST') {
    return sendJson(res, 405, { error: 'Method not allowed' });
  }

  try {
    const body = await readJsonBody(req);
    const hotelId = String(body.hotelId || body.hotel_id || '').trim();
    const roomNumber = normalizeRoom(body.roomNumber || body.room || body.roomId);

    if (!hotelId) {
      return sendJson(res, 400, { error: 'hotelId is required' });
    }
    if (!roomNumber) {
      return sendJson(res, 400, { error: 'roomNumber is required' });
    }

    const { profile, isSuperAdmin } = await requireHotelAccess(req, hotelId);
    const admin = getAdmin();
    const db = admin.firestore();
    const hotelRef = db.collection('Hotels').doc(hotelId);
    const roomRef = hotelRef.collection('Rooms').doc(roomNumber);

    let nextScreens = null;
    await db.runTransaction(async (tx) => {
      const roomSnap = await tx.get(roomRef);
      const hotelSnap = await tx.get(hotelRef);
      const roomData = roomSnap.exists ? roomSnap.data() || {} : {};
      const counted =
        roomData.pairingCounted === true || roomData.pairing_counted === true;

      if (counted && hotelSnap.exists) {
        const current = Math.max(0, Number(hotelSnap.data()?.activeTvScreens) || 0);
        nextScreens = Math.max(0, current - 1);
        tx.set(
          hotelRef,
          {
            activeTvScreens: nextScreens,
            updatedAt: adminSdk.firestore.FieldValue.serverTimestamp(),
          },
          { merge: true },
        );
      }

      // Field names must match the TV listener + admin-panel/js/guests.js unpair payload.
      tx.set(
        roomRef,
        {
          isTvPaired: false,
          is_tv_paired: false,
          pairedDeviceId: '',
          paired_device_id: '',
          pairingCounted: false,
          pairing_counted: false,
          unpairedAt: Date.now(),
          unpairedBy: 'admin_panel',
          unpairedByUid: profile?.uid || '',
          roomNumber,
        },
        { merge: true },
      );
    });

    const rtdbPath = `hotels/${hotelId}/rooms/${roomNumber}`;
    await admin.database().ref(rtdbPath).update({
      session_active: false,
      status: 'UNPAIRED',
      unpairedAt: Date.now(),
      unpairedBy: 'admin_panel',
      unpairedByUid: profile?.uid || '',
    });

    console.info(
      '[tv/unpair] hotel=%s room=%s by=%s super=%s screens=%s',
      hotelId,
      roomNumber,
      (profile?.uid || '').slice(0, 8),
      isSuperAdmin,
      nextScreens,
    );

    return sendJson(res, 200, {
      ok: true,
      hotelId,
      roomNumber,
      activeTvScreens: nextScreens,
    });
  } catch (err) {
    const status = err.statusCode || 500;
    console.error('[tv/unpair]', err?.message || err);
    return sendJson(res, status, {
      error: status >= 500 ? 'Failed to unpair TV' : err.message,
    });
  }
};
