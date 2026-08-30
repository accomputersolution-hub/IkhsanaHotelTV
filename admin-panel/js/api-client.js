/**
 * Thin client for Admin Panel → Vercel serverless APIs.
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

async function getJson(path, { auth: needsAuth = false } = {}) {
  const headers = {};
  if (needsAuth) {
    headers.Authorization = `Bearer ${await getIdToken()}`;
  }
  const res = await fetch(path, { method: 'GET', headers });
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

/** Load TV ticker via Admin SDK (bypasses RTDB client rules). */
export async function fetchAnnouncement(hotelId) {
  const q = encodeURIComponent(String(hotelId || '').trim());
  return getJson(`/api/announcement?hotelId=${q}`, { auth: true });
}

/** Publish TV ticker via Admin SDK (bypasses RTDB client rules). */
export async function publishAnnouncementApi(hotelId, text) {
  return postJson(
    '/api/announcement',
    { hotelId, text },
    { auth: true },
  );
}
