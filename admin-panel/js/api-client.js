/**
 * Thin client for Admin Panel → Vercel serverless password APIs.
 */

import { auth } from './firebase-config.js';

async function getIdToken() {
  const user = auth.currentUser;
  if (!user) throw new Error('You must be signed in');
  return user.getIdToken(true);
}

async function postJson(path, body, { auth: needsAuth = false } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (needsAuth) {
    headers.Authorization = `Bearer ${await getIdToken()}`;
  }
  const res = await fetch(path, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  });
  let data = {};
  try {
    data = await res.json();
  } catch (_) {
    /* ignore */
  }
  if (!res.ok) {
    const err = new Error(data.error || `Request failed (${res.status})`);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}

/** Super Admin / Hotel Admin: instantly set another user's password. */
export async function overrideStaffPassword(uid, newPassword) {
  return postJson(
    '/api/staff/override-password',
    { uid, newPassword },
    { auth: true },
  );
}

/**
 * Forgot-password: generate Admin SDK reset link and email it to sendToEmail.
 * Does not use client sendPasswordResetEmail.
 */
export async function requestCustomPasswordReset(accountEmail, sendToEmail) {
  return postJson('/api/auth/custom-reset', {
    accountEmail,
    sendToEmail,
  });
}
