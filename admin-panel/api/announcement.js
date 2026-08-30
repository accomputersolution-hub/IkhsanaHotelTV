/**
 * GET  /api/announcement?hotelId=…
 * POST /api/announcement  { hotelId, text }
 *
 * Uses Firebase Admin SDK so RTDB client rules cannot block Super Admin /
 * Hotel Admin from the TV ticker node:
 *   hotels/{hotelId}/config/global_announcement
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

function announcementRef(hotelId) {
  const id = normalizeHotelId(hotelId);
  return getAdmin().database().ref(`hotels/${id}/config/global_announcement`);
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
      const snap = await announcementRef(hotelId).get();
      const text = snap.exists() ? String(snap.val() ?? '') : '';
      return sendJson(res, 200, {
        ok: true,
        hotelId,
        path: `hotels/${hotelId}/config/global_announcement`,
        text,
      });
    }

    if (req.method === 'POST') {
      const body = await readJsonBody(req);
      const hotelId = normalizeHotelId(body.hotelId || body.hotel_id || '');
      await requireHotelAdminOrSuper(req, hotelId);
      const text = String(body.text ?? body.message ?? body.value ?? '').trim();
      await announcementRef(hotelId).set(text);
      console.info(
        '[announcement] set hotelId=',
        hotelId,
        'len=',
        text.length,
      );
      return sendJson(res, 200, {
        ok: true,
        hotelId,
        path: `hotels/${hotelId}/config/global_announcement`,
        text,
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
