/**
 * POST /api/auth/custom-reset
 * Body: { accountEmail, sendToEmail }
 *
 * Generates Firebase password-reset link via Admin SDK and emails it through
 * verified SMTP/Resend to sendToEmail (spam-bypass / corporate inbox routing).
 *
 * Optional env:
 * - RESET_LINK_DEST_ALLOWLIST  comma-separated emails allowed as sendTo
 * - RESET_LINK_DEST_DOMAINS    comma-separated domains allowed for sendTo
 * - PASSWORD_RESET_CONTINUE_URL  optional continue URL for the link
 */

const { getAdmin } = require('../_lib/firebaseAdmin');
const { setCors, sendJson, readJsonBody } = require('../_lib/http');
const { sendPasswordResetMail } = require('../_lib/mailer');

/** Best-effort in-memory rate limit (per isolate). */
const recent = new Map();
const RATE_WINDOW_MS = 60_000;
const RATE_MAX = 5;

function rateLimited(key) {
  const now = Date.now();
  const list = (recent.get(key) || []).filter((t) => now - t < RATE_WINDOW_MS);
  if (list.length >= RATE_MAX) {
    recent.set(key, list);
    return true;
  }
  list.push(now);
  recent.set(key, list);
  return false;
}

function normalizeEmail(raw) {
  return String(raw || '').trim().toLowerCase();
}

function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function destAllowed(sendTo) {
  const allowlist = String(process.env.RESET_LINK_DEST_ALLOWLIST || '')
    .split(',')
    .map((s) => s.trim().toLowerCase())
    .filter(Boolean);
  if (allowlist.length && !allowlist.includes(sendTo)) {
    return false;
  }
  const domains = String(process.env.RESET_LINK_DEST_DOMAINS || '')
    .split(',')
    .map((s) => s.trim().toLowerCase())
    .filter(Boolean);
  if (domains.length) {
    const domain = sendTo.split('@')[1] || '';
    if (!domains.includes(domain)) return false;
  }
  return true;
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
    const accountEmail = normalizeEmail(body.accountEmail || body.email);
    const sendToEmail = normalizeEmail(body.sendToEmail || body.sendTo || body.destinationEmail);

    if (!isValidEmail(accountEmail) || !isValidEmail(sendToEmail)) {
      return sendJson(res, 400, { error: 'Valid accountEmail and sendToEmail are required' });
    }
    if (!destAllowed(sendToEmail)) {
      return sendJson(res, 403, {
        error: 'Destination email is not allowed by server policy',
      });
    }

    const ip =
      req.headers['x-forwarded-for']?.toString().split(',')[0]?.trim() ||
      req.socket?.remoteAddress ||
      'unknown';
    if (rateLimited(`${ip}:${accountEmail}`)) {
      return sendJson(res, 429, { error: 'Too many reset requests. Try again later.' });
    }

    const admin = getAdmin();

    // Confirm the Auth account exists (do not leak in client message).
    let userRecord;
    try {
      userRecord = await admin.auth().getUserByEmail(accountEmail);
    } catch (e) {
      if (e?.code === 'auth/user-not-found') {
        return sendJson(res, 200, {
          ok: true,
          message: 'If the account exists, a reset link was sent.',
        });
      }
      throw e;
    }

    const actionCodeSettings = {};
    const continueUrl = process.env.PASSWORD_RESET_CONTINUE_URL;
    if (continueUrl) {
      actionCodeSettings.url = continueUrl;
      actionCodeSettings.handleCodeInApp = false;
    }

    const resetLink = await admin.auth().generatePasswordResetLink(
      accountEmail,
      Object.keys(actionCodeSettings).length ? actionCodeSettings : undefined,
    );

    const mail = await sendPasswordResetMail({
      to: sendToEmail,
      accountEmail,
      resetLink,
    });

    console.info(
      '[custom-reset] account=',
      accountEmail,
      'sendTo=',
      sendToEmail,
      'uid=',
      userRecord.uid.slice(0, 8),
      'via=',
      mail.provider,
    );

    return sendJson(res, 200, {
      ok: true,
      message: 'Reset link sent.',
      sentTo: sendToEmail,
    });
  } catch (err) {
    console.error('[custom-reset]', err?.message || err);
    return sendJson(res, 500, {
      error: err.message?.includes('Mailer not configured')
        ? err.message
        : 'Failed to send reset link',
    });
  }
};
