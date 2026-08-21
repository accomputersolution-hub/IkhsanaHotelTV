import { useCallback, useEffect, useRef, useState } from 'react';
import {
  doc,
  setDoc,
  onSnapshot,
  serverTimestamp,
  deleteDoc,
} from 'firebase/firestore';

const DEVICE_KEY = 'hostity_device_id';
const SESSION_KEY = 'hostity_device_session';
const PAIRING_TTL_MS = 15 * 60 * 1000; // 15 minutes

function randomDigits(length = 6) {
  const max = 10 ** length;
  const n = crypto.getRandomValues(new Uint32Array(1))[0] % max;
  return String(n).padStart(length, '0');
}

export function getOrCreateDeviceId() {
  try {
    let id = localStorage.getItem(DEVICE_KEY);
    if (id && id.length >= 16) return id;
    id =
      typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : `dev_${Date.now()}_${Math.random().toString(36).slice(2)}`;
    localStorage.setItem(DEVICE_KEY, id);
    return id;
  } catch {
    return `dev_${Date.now()}`;
  }
}

export function readDeviceSession() {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed?.hotelId || !parsed?.roomNumber || !parsed?.deviceId) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function clearDeviceSession() {
  try {
    localStorage.removeItem(SESSION_KEY);
  } catch {
    /* ignore */
  }
}

function writeDeviceSession(session) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

/**
 * Kiosk device pairing — never ask the guest to type a room number.
 *
 * Unpaired: create Hotels/{hotelId}/pairing_codes/{code} and show the code.
 * Reception claims the code in admin → writes roomNumber + status=claimed.
 * Kiosk listens, persists session locally, then can access room-scoped data.
 *
 * @param {object} opts
 * @param {import('firebase/firestore').Firestore} opts.db
 * @param {string} opts.hotelId  Internal id from public config (not the subdomain)
 * @param {string} [opts.publicSlug]
 */
export function useDevicePairing({ db, hotelId, publicSlug = '' } = {}) {
  const deviceId = useRef(getOrCreateDeviceId()).current;
  const [session, setSession] = useState(() => readDeviceSession());
  const [pairingCode, setPairingCode] = useState(null);
  const [status, setStatus] = useState(() =>
    readDeviceSession()?.hotelId === hotelId ? 'paired' : 'idle',
  );
  const [error, setError] = useState(null);
  const [expiresAt, setExpiresAt] = useState(null);
  const unsubRef = useRef(null);

  const stopListening = useCallback(() => {
    if (unsubRef.current) {
      unsubRef.current();
      unsubRef.current = null;
    }
  }, []);

  const unpair = useCallback(async () => {
    stopListening();
    clearDeviceSession();
    setSession(null);
    setPairingCode(null);
    setExpiresAt(null);
    setStatus('idle');
    setError(null);
  }, [stopListening]);

  const startPairing = useCallback(async () => {
    if (!db || !hotelId) {
      setError('Missing hotel context for pairing');
      setStatus('error');
      return;
    }

    stopListening();
    setError(null);
    setStatus('pairing');

    const code = randomDigits(6);
    const expires = Date.now() + PAIRING_TTL_MS;
    const ref = doc(db, 'Hotels', hotelId, 'pairing_codes', code);

    try {
      await setDoc(ref, {
        code,
        hotelId,
        publicSlug: publicSlug || '',
        deviceId,
        status: 'pending',
        roomNumber: null,
        createdAt: serverTimestamp(),
        expiresAt: expires,
        claimedAt: null,
        claimedBy: null,
      });
    } catch (err) {
      setError(err?.message || 'Could not create pairing code');
      setStatus('error');
      return;
    }

    setPairingCode(code);
    setExpiresAt(expires);

    unsubRef.current = onSnapshot(
      ref,
      async (snap) => {
        if (!snap.exists()) {
          setError('Pairing code expired or removed');
          setStatus('error');
          setPairingCode(null);
          return;
        }
        const data = snap.data() || {};
        if (data.deviceId && data.deviceId !== deviceId) {
          setError('This code is bound to another device');
          setStatus('error');
          return;
        }
        if (data.expiresAt && Number(data.expiresAt) < Date.now() && data.status !== 'claimed') {
          setError('Pairing code expired — generate a new one');
          setStatus('error');
          return;
        }
        if (data.status === 'claimed' && data.roomNumber) {
          const next = {
            hotelId,
            publicSlug: publicSlug || data.publicSlug || '',
            roomNumber: String(data.roomNumber).trim(),
            deviceId,
            pairedAt: Date.now(),
            pairingCode: code,
          };
          writeDeviceSession(next);
          setSession(next);
          setStatus('paired');
          setPairingCode(null);
          stopListening();
          // Best-effort cleanup of consumed code
          try {
            await deleteDoc(ref);
          } catch {
            /* staff rules may block delete — ok */
          }
        }
      },
      (err) => {
        setError(err?.message || 'Pairing listener failed');
        setStatus('error');
      },
    );
  }, [db, hotelId, publicSlug, deviceId, stopListening]);

  // Auto-start pairing when hotel is known and device is unpaired
  useEffect(() => {
    if (!hotelId) return undefined;
    const existing = readDeviceSession();
    if (existing?.hotelId === hotelId && existing?.roomNumber) {
      setSession(existing);
      setStatus('paired');
      return undefined;
    }
    // Different hotel than stored session → clear stale binding
    if (existing && existing.hotelId !== hotelId) {
      clearDeviceSession();
      setSession(null);
    }
    startPairing();
    return () => stopListening();
  }, [hotelId, startPairing, stopListening]);

  return {
    status, // idle | pairing | paired | error
    pairingCode,
    expiresAt,
    session,
    deviceId,
    error,
    isPaired: status === 'paired' && Boolean(session?.roomNumber),
    startPairing,
    unpair,
  };
}
