import { initializeApp } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-app.js';
import { getAuth } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-auth.js';
import { getFirestore } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import { getDatabase } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-database.js';
import { getStorage } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-storage.js';

export const firebaseConfig = {
  apiKey: 'AIzaSyBEXhPG6aNiJ1S7pN4EDoBo6EYMtbNe-pQ',
  authDomain: 'ikhsana-hotel-tv.firebaseapp.com',
  databaseURL: 'https://ikhsana-hotel-tv-default-rtdb.asia-southeast1.firebasedatabase.app',
  projectId: 'ikhsana-hotel-tv',
  storageBucket: 'ikhsana-hotel-tv.firebasestorage.app',
};

/** Fallback / canonical slug — must match Android HotelConfig.HOTEL_ID exactly */
export const DEFAULT_HOTEL_ID = 'ikhsana_001';

/** @deprecated Prefer TenantManager.getHotelId() — same value as DEFAULT_HOTEL_ID */
export const HOTEL_ID = DEFAULT_HOTEL_ID;

/**
 * Canonical hotel slug: trim, lowercase, hyphens → underscores.
 * Legacy aliases `ikhsana` / `ikhsana-001` map to DEFAULT_HOTEL_ID (`ikhsana_001`).
 * Keep in sync with Android HotelConfig.normalizeHotelId.
 */
export function normalizeHotelId(raw) {
  const cleaned = String(raw ?? '')
    .trim()
    .toLowerCase()
    .replace(/-/g, '_');
  if (!cleaned || cleaned === 'ikhsana' || cleaned === 'ikhsana001') {
    return DEFAULT_HOTEL_ID;
  }
  return cleaned;
}

export const app = initializeApp(firebaseConfig);
export const db = getFirestore(app);
/** Realtime Database — TV kiosk Lock Task flags (`hotels/{hotelId}/config/…`) */
export const rtdb = getDatabase(app);
/** Cloud Storage — hotel logos / wallpapers uploaded from Super Admin */
export const storage = getStorage(app);

/**
 * Primary Auth instance — Email/Password must be enabled in Firebase Console.
 * Hotel-admin creation uses a secondary app in auth.js so Super Admin stays signed in.
 */
export const auth = getAuth(app);

/** Secondary Auth app for provisioning hotel admins without swapping the active session */
export const secondaryApp = initializeApp(firebaseConfig, 'SecondaryAuth');
export const secondaryAuth = getAuth(secondaryApp);
