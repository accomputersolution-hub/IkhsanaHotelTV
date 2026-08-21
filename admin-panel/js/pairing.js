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
import { db } from './firebase-config.js';
import { getHotelId } from './tenant-context.js';
import { toast, openModal, closeModal, setupModalClose } from './utils.js';
import { canAccessModule } from './rbac.js';

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

async function onClaimSubmit(e) {
  e.preventDefault();
  const hotelId = getHotelId();
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

  btn.disabled = true;
  try {
    const ref = doc(db, 'Hotels', hotelId, 'pairing_codes', code);
    const snap = await getDoc(ref);
    if (!snap.exists()) {
      toast('Code not found or expired', 'error');
      return;
    }
    const data = snap.data() || {};
    if (data.status === 'claimed') {
      toast('Code already claimed', 'error');
      return;
    }
    if (data.expiresAt && Number(data.expiresAt) < Date.now()) {
      toast('Code expired — ask the kiosk to generate a new one', 'error');
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
