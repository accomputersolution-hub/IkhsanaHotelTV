/**
 * GET  /api/announcement?hotelId=…
 * POST /api/announcement  { hotelId, text }
 *
 * Publishes the Home Screen ticker so TVs actually see it:
 * 1. Firestore Hotels/{hotelId} announcement fields (TV branding listener — reliable)
 * 2. RTDB hotels/{hotelId}/config/global_announcement (new path)
 * 3. RTDB hotel_settings/{hotelId}/global_announcement (legacy TVs)
 *
 * Admin SDK bypasses client RTDB rules that block browser/TV reads on some nodes.
 */

const { getAdmin } = require('./_lib/firebaseAdmin');
const { setCors, sendJson, readJsonBody } = require('./_lib/http');
const { requireHotelAdminOrSuper } = require('./_lib/verifyCaller');

function normalizeHotelId(raw) {
  return String(raw || '')
    .trim()
    .toLowerCase()
    .replace(/-/g, '_');
}

function rtdbPaths(hotelId) {
  const id = normalizeHotelId(hotelId);
  return {
    id,
    primary: `hotels/${id}/config/global_announcement`,
    legacy: `hotel_settings/${id}/global_announcement`,
  };
}

async function readAnnouncement(hotelId) {
  const { id, primary, legacy } = rtdbPaths(hotelId);
  const admin = getAdmin();
  const db = admin.database();

  const primarySnap = await db.ref(primary).get();
  if (primarySnap.exists() && primarySnap.val() != null) {
    return String(primarySnap.val());
  }

  const legacySnap = await db.ref(legacy).get();
  if (legacySnap.exists() && legacySnap.val() != null) {
    return String(legacySnap.val());
  }

  const fsSnap = await admin.firestore().collection('Hotels').doc(id).get();
  if (fsSnap.exists) {
    const data = fsSnap.data() || {};
    const branding =
      data.branding && typeof data.branding === 'object' ? data.branding : {};
    const fromFs =
      data.announcement ||
      data.announcement_text ||
      data.ticker_text ||
      data.tickerText ||
      branding.announcement ||
      branding.announcement_text ||
      '';
    if (fromFs) return String(fromFs);
  }
  return '';
}

async function publishAnnouncement(hotelId, text) {
  const { id, primary, legacy } = rtdbPaths(hotelId);
  const admin = getAdmin();
  const db = admin.database();
  const trimmed = String(text ?? '').trim();

  const writes = [
    db.ref(primary).set(trimmed),
    db.ref(legacy).set(trimmed),
    admin
      .firestore()
      .collection('Hotels')
      .doc(id)
      .set(
        {
          // Top-level fields — TV HomeViewModel → branding.announcement → ticker
          announcement: trimmed,
          announcement_text: trimmed,
          ticker_text: trimmed,
          tickerText: trimmed,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true },
      ),
  ];

  const results = await Promise.allSettled(writes);
  const failed = results.filter((r) => r.status === 'rejected');
  if (failed.length === results.length) {
    const err = new Error(
      failed[0].reason?.message || 'Failed to publish ticker to Firebase',
    );
    err.statusCode = 500;
    throw err;
  }
  for (const r of results) {
    if (r.status === 'rejected') {
      console.warn('[announcement] partial write failed:', r.reason?.message || r.reason);
    }
  }
  return { id, primary, legacy, text: trimmed };
}

module.exports = async function handler(req, res) {
  setCors(res);
  if (req.method === 'OPTIONS') {
    res.statusCode = 204;
    res.end();
    return;
  }

  try {
    if (req.method === 'GET') {
      const hotelId = normalizeHotelId(
        req.query?.hotelId || req.query?.hotel_id || '',
      );
      await requireHotelAdminOrSuper(req, hotelId);
      const text = await readAnnouncement(hotelId);
      const { primary } = rtdbPaths(hotelId);
      return sendJson(res, 200, {
        ok: true,
        hotelId,
        path: primary,
        text,
      });
    }

    if (req.method === 'POST') {
      const body = await readJsonBody(req);
      const hotelId = normalizeHotelId(body.hotelId || body.hotel_id || '');
      await requireHotelAdminOrSuper(req, hotelId);
      const text = String(body.text ?? body.message ?? body.value ?? '').trim();
      const published = await publishAnnouncement(hotelId, text);
      console.info(
        '[announcement] published hotelId=',
        published.id,
        'len=',
        published.text.length,
      );
      return sendJson(res, 200, {
        ok: true,
        hotelId: published.id,
        path: published.primary,
        text: published.text,
      });
    }

    return sendJson(res, 405, { error: 'Method not allowed' });
  } catch (err) {
    const status = err.statusCode || 500;
    console.error('[announcement]', err?.message || err);
    return sendJson(res, status, {
      error: status >= 500 ? 'Failed to update TV ticker' : err.message,
    });
  }
};
