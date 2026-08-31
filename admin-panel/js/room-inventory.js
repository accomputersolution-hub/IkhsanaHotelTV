/**
 * Super Admin is the only source of room inventory.
 * Managed rooms have managedBy === 'super_admin' and/or a non-blank category
 * (legacy SA-created rooms always wrote `category`).
 */

import { db, rtdb } from './firebase-config.js';
import {
  doc,
  writeBatch,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import { ref as rtdbRef, update as rtdbUpdate } from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-database.js';

export const SUPER_ADMIN_ROOM_MANAGER = 'super_admin';

const BATCH_LIMIT = 400;

/**
 * @param {Record<string, any> | null | undefined} data
 * @returns {boolean}
 */
export function isSuperAdminManagedRoom(data) {
  if (!data || typeof data !== 'object') return false;
  if (String(data.managedBy || '').trim() === SUPER_ADMIN_ROOM_MANAGER) return true;
  const category = String(data.category || data.floor || '').trim();
  return category.length > 0;
}

/**
 * Deletes Rooms docs that were auto-created by pairing / hotel PMS
 * (no SA `category` / `managedBy`).
 *
 * @param {string} hotelId
 * @param {Array<{ id: string, [key: string]: any }>} rooms
 * @returns {Promise<number>} number deleted
 */
export async function purgeUnmanagedRooms(hotelId, rooms) {
  const orphans = (rooms || []).filter((r) => !isSuperAdminManagedRoom(r));
  if (!orphans.length) return 0;

  let deleted = 0;
  for (let i = 0; i < orphans.length; i += BATCH_LIMIT) {
    const chunk = orphans.slice(i, i + BATCH_LIMIT);
    const batch = writeBatch(db);
    for (const room of chunk) {
      batch.delete(doc(db, 'Hotels', hotelId, 'Rooms', String(room.id)));
    }
    await batch.commit();
    deleted += chunk.length;
  }

  await Promise.all(
    orphans.map(async (room) => {
      const roomId = String(room.id);
      try {
        await rtdbUpdate(rtdbRef(rtdb, `hotels/${hotelId}/rooms/${roomId}`), {
          session_active: false,
          status: 'UNPAIRED',
          purgedAt: Date.now(),
          purgedBy: 'super_admin_inventory',
        });
      } catch (err) {
        console.warn('[room-inventory] RTDB purge skipped for', roomId, err);
      }
    }),
  );

  console.info(
    `[room-inventory] purged ${deleted} unmanaged room(s) for Hotels/${hotelId}`,
    orphans.map((r) => r.id),
  );
  return deleted;
}
