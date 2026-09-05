/**
 * SMTP / Resend mailer for custom password-reset delivery.
 *
 * Env:
 * - SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS, SMTP_FROM
 * - or RESEND_API_KEY (+ SMTP_FROM / RESEND_FROM)
 */

const nodemailer = require('nodemailer');

function fromAddress() {
  return (
    process.env.SMTP_FROM ||
    process.env.RESEND_FROM ||
    process.env.SMTP_USER ||
    'noreply@pcncloud.in'
  );
}

async function sendViaResend({ to, subject, text, html }) {
  const key = process.env.RESEND_API_KEY;
  if (!key) return false;
  const res = await fetch('https://api.resend.com/emails', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${key}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      from: fromAddress(),
      to: [to],
      subject,
      text,
      html,
    }),
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`Resend failed (${res.status}): ${body}`);
  }
  return true;
}

async function sendViaSmtp({ to, subject, text, html }) {
  const host = process.env.SMTP_HOST;
  const user = process.env.SMTP_USER;
  const pass = process.env.SMTP_PASS;
  if (!host || !user || !pass) {
    throw new Error(
      'Mailer not configured. Set SMTP_HOST/SMTP_USER/SMTP_PASS or RESEND_API_KEY',
    );
  }
  const port = Number(process.env.SMTP_PORT || 587);
  const transporter = nodemailer.createTransport({
    host,
    port,
    secure: port === 465,
    auth: { user, pass },
  });
  await transporter.sendMail({
    from: fromAddress(),
    to,
    subject,
    text,
    html,
  });
  return true;
}

async function sendPasswordResetMail({ to, accountEmail, resetLink }) {
  const subject = 'Hostity Admin — Password reset link';
  const text =
    `A password reset was requested for ${accountEmail}.\n\n` +
    `Open this link to set a new password:\n${resetLink}\n\n` +
    `If you did not request this, ignore this email.`;
  const html =
    `<p>A password reset was requested for <strong>${escapeHtml(accountEmail)}</strong>.</p>` +
    `<p><a href="${escapeAttr(resetLink)}">Reset your password</a></p>` +
    `<p style="word-break:break-all;font-size:12px;color:#64748b">${escapeHtml(resetLink)}</p>` +
    `<p>If you did not request this, ignore this email.</p>`;

  if (process.env.RESEND_API_KEY) {
    await sendViaResend({ to, subject, text, html });
    return { provider: 'resend' };
  }
  await sendViaSmtp({ to, subject, text, html });
  return { provider: 'smtp' };
}

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function escapeAttr(s) {
  return escapeHtml(s).replace(/'/g, '&#39;');
}

module.exports = { sendPasswordResetMail, fromAddress };
