/**
 * Reception: claim a kiosk pairing code → bind roomNumber.
 * Rejects rooms that already have an active TV pairing.
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
import { normalizeRoom, formatRoomLabel } from './paths.js';
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

function isRoomAlreadyPaired(roomData) {
  if (!roomData || typeof roomData !== 'object') return false;
  const device = String(roomData.pairedDeviceId || roomData.paired_device_id || '').trim();
  const flagged =
    roomData.isTvPaired === true ||
    roomData.is_tv_paired === true ||
    roomData.pairingCounted === true ||
    roomData.pairing_counted === true;
  return Boolean(device) || flagged;
}

async function onClaimSubmit(e) {
  e.preventDefault();
  const hotelId = getHotelId();
  const code = String(document.getElementById('pairing-code-input')?.value || '')
    .trim()
    .replace(/\D/g, '');
  const roomNumber = normalizeRoom(document.getElementById('pairing-room-input')?.value);
  const btn = e.target.querySelector('button[type="submit"]');

  if (!/^\d{6}$/.test(code)) {
    toast('Enter the 6-digit code from the TV / kiosk', 'error');
    return;
  }
  if (!roomNumber) {
    toast('Room / location name is required', 'error');
    return;
  }

  btn.disabled = true;
  try {
    const roomRef = doc(db, 'Hotels', hotelId, 'Rooms', roomNumber);
    const roomSnap = await getDoc(roomRef);
    if (roomSnap.exists() && isRoomAlreadyPaired(roomSnap.data())) {
      toast('Already paired with another TV. Please unpair first.', 'error');
      return;
    }

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
      roomNumber, // String id — numeric ("101") or named ("Middle East")
      room_number: roomNumber,
      claimedAt: serverTimestamp(),
      claimedBy: 'staff_admin',
    });

    toast(`Code claimed for ${formatRoomLabel(roomNumber)} — waiting for TV…`);
    closeModal('pairing-claim-modal');
  } catch (err) {
    console.error('[pairing] claim failed', err);
    toast(err.message || 'Failed to claim pairing code', 'error');
  } finally {
    btn.disabled = false;
  }
}
