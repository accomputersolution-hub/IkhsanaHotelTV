import { db } from './firebase-config.js';
import {
  collection,
  query,
  where,
  getDocs,
  writeBatch,
  serverTimestamp,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import { normalizeRoom, logFirestoreWrite } from './paths.js';
import { getHotelId } from './tenant-context.js';

const BATCH_LIMIT = 450;

/** Unique session id for a new guest stay — TV app watches this field. */
export function newSessionKey(roomNumber) {
  return `${normalizeRoom(roomNumber)}_${Date.now()}`;
}

/**
 * Archive / dismiss all active guest-scoped data for a room.
 * Called on check-out and before a new check-in.
 */
export async function flushRoomSession(roomNumber) {
  const room = normalizeRoom(roomNumber);
  let totalOps = 0;

  totalOps += await archiveRoomAlerts(room);
  totalOps += await archiveRoomOrders(room);
  totalOps += await archiveRoomRequests(room);

  logFirestoreWrite('Session Flush', `Hotels/${getHotelId()}/Rooms/${room}`, {
    archivedAlerts: true,
    archivedOrders: true,
    archivedRequests: true,
    totalOps,
  });

  return totalOps;
}

async function archiveRoomAlerts(roomNumber) {
  const snap = await getDocs(
    query(
      collection(db, 'Hotels', getHotelId(), 'Alerts'),
      where('roomNumber', '==', roomNumber),
    ),
  );

  const updates = [];
  snap.forEach((docSnap) => {
    const data = docSnap.data();
    if (!data.read || !data.revoked) {
      updates.push({
        ref: docSnap.ref,
        data: {
          read: true,
          revoked: true,
          archived: true,
          archivedAt: serverTimestamp(),
        },
      });
    }
  });

  return commitBatches(updates);
}

async function archiveRoomOrders(roomNumber) {
  const hotelId = getHotelId();
  const snap = await getDocs(
    query(collection(db, 'Live_Orders'), where('roomNumber', '==', roomNumber)),
  );

  const updates = [];
  snap.forEach((docSnap) => {
    const data = docSnap.data();
    if (data.hotelId && data.hotelId !== hotelId) return;
    if (!data.archived) {
      updates.push({
        ref: docSnap.ref,
        data: {
          archived: true,
          archivedAt: serverTimestamp(),
        },
      });
    }
  });

  return commitBatches(updates);
}

async function archiveRoomRequests(roomNumber) {
  const snap = await getDocs(
    query(
      collection(db, 'Hotels', getHotelId(), 'Requests'),
      where('roomNumber', '==', roomNumber),
    ),
  );

  const updates = [];
  snap.forEach((docSnap) => {
    const data = docSnap.data();
    const terminal = data.status === 'completed' || data.status === 'cancelled';
    if (!terminal || !data.archived) {
      updates.push({
        ref: docSnap.ref,
        data: {
          status: terminal ? data.status : 'cancelled',
          archived: true,
          updatedAt: serverTimestamp(),
          archivedAt: serverTimestamp(),
        },
      });
    }
  });

  return commitBatches(updates);
}

async function commitBatches(updates) {
  if (!updates.length) return 0;

  let committed = 0;
  for (let i = 0; i < updates.length; i += BATCH_LIMIT) {
    const batch = writeBatch(db);
    const chunk = updates.slice(i, i + BATCH_LIMIT);
    chunk.forEach(({ ref, data }) => batch.update(ref, data));
    await batch.commit();
    committed += chunk.length;
  }
  return committed;
}
