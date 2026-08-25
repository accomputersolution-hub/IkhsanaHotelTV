/**
 * POST /api/staff/override-password
 * Body: { uid, newPassword }
 * Auth: Bearer Firebase ID token (Super Admin or Hotel Admin for that hotel)
 *
 * Instantly overwrites the staff Auth password via Admin SDK — no email.
 */

const { getAdmin } = require('../_lib/firebaseAdmin');
const { setCors, sendJson, readJsonBody } = require('../_lib/http');
const { requirePasswordManager } = require('../_lib/verifyCaller');

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
    const uid = String(body.uid || body.staffUid || '').trim();
    const newPassword = String(body.newPassword || body.password || '');

    if (!uid) {
      return sendJson(res, 400, { error: 'uid is required' });
    }
    if (newPassword.length < 6) {
      return sendJson(res, 400, { error: 'Password must be at least 6 characters' });
    }
    if (newPassword.length > 128) {
      return sendJson(res, 400, { error: 'Password is too long' });
    }

    await requirePasswordManager(req, { targetUid: uid });

    const admin = getAdmin();
    await admin.auth().updateUser(uid, { password: newPassword });

    console.info('[override-password] updated uid=', uid.slice(0, 8));
    return sendJson(res, 200, { ok: true, uid });
  } catch (err) {
    const status = err.statusCode || 500;
    console.error('[override-password]', err?.message || err);
    return sendJson(res, status, {
      error: status >= 500 ? 'Failed to update password' : err.message,
    });
  }
};
