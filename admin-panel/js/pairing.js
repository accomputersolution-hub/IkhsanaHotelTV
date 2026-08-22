/**
 * Reception: claim a kiosk pairing code → bind roomNumber.
 * After claim, mint Custom Claims via Cloud Function (recommended) so the
 * device can read Hotels/{hotelId}/Rooms/{room} under security rules.
 */

import {
  doc,
  getDoc,
  updateDoc,
  serverTimestamp,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import { db, normalizeHotelId } from './firebase-config.js';
import { getHotelId, getHotelMeta } from './tenant-context.js';
import { toast, openModal, closeModal, setupModalClose } from './utils.js';
import { canAccessModule } from './rbac.js';
import { isPairingCodeExpired } from './pairing-utils.js';
import { fetchPublicHotelBySlug } from './hotel-tenant.js';

export function initPairingClaim() {
  setupModalClose('pairing-claim-modal', 'pairing-claim-close');

  document.getElementById('claim-pairing-btn')?.addEventListener('click', () => {
    if (!canAccessModule('pms')) {
      toast('Reception / admin access required', 'error');
      return;
    }
    document.getElementById('pairing-claim-form')?.reset();
    openModal('pairing-claim-modal');
  });

  document.getElementById('pairing-claim-form')?.addEventListener('submit', onClaimSubmit);
}

/** Resolve Hotels/{id} used for pairing — aligns admin tenant with public_hotels slug. */
async function resolvePairingHotelId() {
  const activeId = normalizeHotelId(getHotelId());
  const meta = getHotelMeta() || {};
  const slug = meta.publicSlug || meta.public_slug;
  if (!slug) return activeId;

  try {
    const pub = await fetchPublicHotelBySlug(slug);
    const pubId = normalizeHotelId(pub?.hotelId);
    if (pubId) return pubId;
  } catch (err) {
    console.warn('[pairing] public_hotels lookup failed', err);
  }
  return activeId;
}

async function loadPairingCodeDoc(code, preferredHotelId) {
  const tried = new Set();

  async function tryHotel(hotelId) {
    const id = normalizeHotelId(hotelId);
    if (!id || tried.has(id)) return null;
    tried.add(id);

    const ref = doc(db, 'Hotels', id, 'pairing_codes', code);
    const snap = await getDoc(ref);
    if (!snap.exists()) return null;

    const data = snap.data() || {};
    const docHotelId = normalizeHotelId(data.hotelId) || id;

    // Doc may live under the hotelId embedded by the TV even if admin tenant differs.
    if (docHotelId !== id) {
      const canonical = await tryHotel(docHotelId);
      if (canonical) return canonical;
    }

    return { ref, snap, data, hotelId: id };
  }

  let found = await tryHotel(preferredHotelId);
  if (found) return found;

  const activeId = normalizeHotelId(getHotelId());
  if (activeId && activeId !== normalizeHotelId(preferredHotelId)) {
    found = await tryHotel(activeId);
  }
  return found;
}

async function onClaimSubmit(e) {
  e.preventDefault();
  const pairingHotelId = await resolvePairingHotelId();
  const code = String(document.getElementById('pairing-code-input')?.value || '')
    .trim()
    .replace(/\D/g, '');
  const roomNumber = String(document.getElementById('pairing-room-input')?.value || '').trim();
  const btn = e.target.querySelector('button[type="submit"]');

  if (!/^\d{6}$/.test(code)) {
    toast('Enter the 6-digit code from the TV / kiosk', 'error');
    return;
  }
  if (!roomNumber) {
    toast('Room number is required', 'error');
    return;
  }
  if (!pairingHotelId) {
    toast('No active hotel — reload admin and try again', 'error');
    return;
  }

  btn.disabled = true;
  try {
    const loaded = await loadPairingCodeDoc(code, pairingHotelId);
    if (!loaded) {
      toast('Code not found — check TV slug matches this property', 'error');
      return;
    }

    const { ref, data } = loaded;
    const codeHotelId = normalizeHotelId(data.hotelId);
    const activeHotelId = normalizeHotelId(getHotelId());

    if (codeHotelId && activeHotelId && codeHotelId !== activeHotelId) {
      toast(
        `This code belongs to property “${codeHotelId}”. Open that hotel in admin, or re-enter slug on the TV.`,
        'error',
      );
      return;
    }

    if (data.status === 'claimed') {
      toast('Code already claimed', 'error');
      return;
    }

    if (isPairingCodeExpired(data)) {
      toast('Code expired — tap “Generate new code” on the TV and try again', 'error');
      return;
    }

    await updateDoc(ref, {
      status: 'claimed',
      roomNumber,
      claimedAt: serverTimestamp(),
      claimedBy: 'staff_admin',
    });

    toast(`Device paired to Room ${roomNumber}`);
    closeModal('pairing-claim-modal');
  } catch (err) {
    console.error('[pairing] claim failed', err);
    toast(err.message || 'Failed to claim pairing code', 'error');
  } finally {
    btn.disabled = false;
  }
}
