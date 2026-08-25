/**
 * Firebase Admin singleton for Vercel serverless routes.
 *
 * Env (pick one):
 * - FIREBASE_SERVICE_ACCOUNT_JSON  → full service-account JSON string
 * - FIREBASE_PROJECT_ID + FIREBASE_CLIENT_EMAIL + FIREBASE_PRIVATE_KEY
 */

const admin = require('firebase-admin');

function loadCredential() {
  const jsonRaw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (jsonRaw) {
    const parsed = typeof jsonRaw === 'string' ? JSON.parse(jsonRaw) : jsonRaw;
    if (parsed.private_key) {
      parsed.private_key = String(parsed.private_key).replace(/\\n/g, '\n');
    }
    return admin.credential.cert(parsed);
  }

  const projectId = process.env.FIREBASE_PROJECT_ID || process.env.GCLOUD_PROJECT;
  const clientEmail = process.env.FIREBASE_CLIENT_EMAIL;
  let privateKey = process.env.FIREBASE_PRIVATE_KEY;
  if (!projectId || !clientEmail || !privateKey) {
    throw new Error(
      'Missing Firebase Admin credentials. Set FIREBASE_SERVICE_ACCOUNT_JSON ' +
        'or FIREBASE_PROJECT_ID + FIREBASE_CLIENT_EMAIL + FIREBASE_PRIVATE_KEY',
    );
  }
  privateKey = privateKey.replace(/\\n/g, '\n');
  return admin.credential.cert({
    projectId,
    clientEmail,
    privateKey,
  });
}

function getAdmin() {
  if (admin.apps.length) return admin;
  admin.initializeApp({
    credential: loadCredential(),
    projectId: process.env.FIREBASE_PROJECT_ID || 'ikhsana-hotel-tv',
    databaseURL:
      process.env.FIREBASE_DATABASE_URL ||
      'https://ikhsana-hotel-tv-default-rtdb.asia-southeast1.firebasedatabase.app',
  });
  return admin;
}

module.exports = { getAdmin, admin };
