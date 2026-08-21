import { useEffect, useState } from 'react';
import { useDevicePairing } from '../hooks/useDevicePairing.js';

/**
 * Full-screen pairing UI — shows a 6-digit code until reception claims it.
 */
export function DevicePairingScreen({
  db,
  hotel,
  onPaired,
}) {
  const pairing = useDevicePairing({
    db,
    hotelId: hotel?.hotelId,
    publicSlug: hotel?.publicSlug,
  });

  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!pairing.expiresAt || pairing.status !== 'pairing') return undefined;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [pairing.expiresAt, pairing.status]);

  useEffect(() => {
    if (pairing.isPaired && pairing.session && typeof onPaired === 'function') {
      onPaired(pairing.session);
    }
  }, [pairing.isPaired, pairing.session, onPaired]);

  if (pairing.isPaired) {
    return null;
  }

  const secondsLeft = pairing.expiresAt
    ? Math.max(0, Math.floor((pairing.expiresAt - now) / 1000))
    : 0;
  const mm = String(Math.floor(secondsLeft / 60)).padStart(2, '0');
  const ss = String(secondsLeft % 60).padStart(2, '0');

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        padding: 28,
        background: hotel?.bgWallpaper
          ? `linear-gradient(rgba(11,19,37,.72), rgba(11,19,37,.88)), url(${hotel.bgWallpaper}) center/cover`
          : 'radial-gradient(ellipse at top, #1a2744 0%, #0B1325 70%)',
        color: '#F1F5F9',
        fontFamily: 'Georgia, "Times New Roman", serif',
        textAlign: 'center',
      }}
    >
      <div style={{ maxWidth: 480, width: '100%' }}>
        {hotel?.logoUrl ? (
          <img
            src={hotel.logoUrl}
            alt={hotel.name || 'Hotel'}
            style={{ height: 56, objectFit: 'contain', marginBottom: 20 }}
          />
        ) : null}

        <p
          style={{
            margin: 0,
            fontSize: 12,
            letterSpacing: '0.14em',
            textTransform: 'uppercase',
            color: hotel?.themeColor || '#E8D5A3',
            fontWeight: 700,
            fontFamily: 'system-ui, sans-serif',
          }}
        >
          {hotel?.name || 'Guest kiosk'}
        </p>

        <h1
          style={{
            margin: '12px 0 8px',
            fontSize: 32,
            fontWeight: 700,
            color: '#F8FAFC',
          }}
        >
          Pair this device
        </h1>
        <p
          style={{
            margin: '0 auto 28px',
            maxWidth: 360,
            fontSize: 15,
            lineHeight: 1.5,
            color: '#94A3B8',
            fontFamily: 'system-ui, sans-serif',
          }}
        >
          Ask reception to enter this code in Staff Admin. Do not type a room
          number on the kiosk.
        </p>

        <div
          style={{
            display: 'inline-flex',
            gap: 10,
            padding: '18px 22px',
            borderRadius: 18,
            border: `1px solid ${(hotel?.themeColor || '#C9A962')}66`,
            background: 'rgba(11,19,37,0.72)',
            boxShadow: '0 18px 50px rgba(0,0,0,0.35)',
            marginBottom: 16,
          }}
          aria-live="polite"
          aria-label="Pairing code"
        >
          {(pairing.pairingCode || '------').split('').map((digit, i) => (
            <span
              key={`${digit}-${i}`}
              style={{
                width: 44,
                height: 56,
                display: 'grid',
                placeItems: 'center',
                borderRadius: 12,
                background: 'rgba(255,255,255,0.06)',
                border: '1px solid rgba(255,255,255,0.08)',
                fontSize: 28,
                fontWeight: 800,
                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                color: hotel?.themeColor || '#E8D5A3',
              }}
            >
              {digit}
            </span>
          ))}
        </div>

        {pairing.status === 'pairing' && pairing.expiresAt ? (
          <p style={{ margin: '0 0 18px', color: '#64748B', fontFamily: 'system-ui, sans-serif', fontSize: 13 }}>
            Code expires in {mm}:{ss}
          </p>
        ) : null}

        {pairing.error ? (
          <p style={{ color: '#FCA5A5', fontFamily: 'system-ui, sans-serif', fontSize: 14 }}>
            {pairing.error}
          </p>
        ) : null}

        <button
          type="button"
          onClick={() => pairing.startPairing()}
          style={{
            marginTop: 8,
            padding: '12px 20px',
            borderRadius: 12,
            border: '1px solid rgba(201,169,98,0.45)',
            background: 'rgba(201,169,98,0.16)',
            color: '#E8D5A3',
            fontWeight: 700,
            fontFamily: 'system-ui, sans-serif',
            cursor: 'pointer',
          }}
        >
          Generate new code
        </button>
      </div>
    </div>
  );
}
