/**
 * Shared pairing-code helpers (admin claim + expiry checks).
 */

/** Must match Android PairingActivity.CODE_TTL_MS and kiosk-web PAIRING_TTL_MS */
export const PAIRING_TTL_MS = 15 * 60 * 1000;

/** Normalize Firestore Timestamp / seconds / ms → epoch ms. */
export function parseTimestampMs(raw) {
  if (raw == null) return null;
  if (typeof raw?.toMillis === 'function') return raw.toMillis();
  if (typeof raw?.seconds === 'number') return raw.seconds * 1000;
  if (typeof raw === 'number' && Number.isFinite(raw)) {
    return raw < 1e12 ? raw * 1000 : raw;
  }
  const n = Number(raw);
  if (!Number.isFinite(n)) return null;
  return n < 1e12 ? n * 1000 : n;
}

/**
 * Prefer server `createdAt + TTL` so a TV with a wrong system clock cannot
 * mint codes that look instantly expired on admin (which uses correct time).
 */
export function isPairingCodeExpired(data = {}) {
  if (data.status === 'claimed') return false;

  const now = Date.now();
  const created = parseTimestampMs(data.createdAt);
  if (created != null) {
    return created + PAIRING_TTL_MS < now;
  }

  const expires = parseTimestampMs(data.expiresAt);
  if (expires != null) {
    return expires < now;
  }

  return false;
}
