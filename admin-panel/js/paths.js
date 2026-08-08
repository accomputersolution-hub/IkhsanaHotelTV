import { getHotelId } from './tenant-context.js';
import { TenantManager } from './tenant-context.js';
import { DEFAULT_HOTEL_ID, normalizeHotelId } from './firebase-config.js';

export { normalizeHotelId, DEFAULT_HOTEL_ID };

/** Must stay in sync with FirestorePaths.kt — capital "Hotels" only */
export const HOTELS_ROOT = 'Hotels';

export function normalizeRoom(roomNumber) {
  return String(roomNumber ?? '').trim();
}

function hotelRoot() {
  const id = normalizeHotelId(getHotelId());
  if (!id) throw new Error('No active hotel context — sign in or select a hotel');
  return `${HOTELS_ROOT}/${id}`;
}

export const paths = {
  hotelsCollection: () => HOTELS_ROOT,
  hotelDoc: (hotelId = getHotelId()) => `${HOTELS_ROOT}/${normalizeHotelId(hotelId)}`,
  roomDoc: (roomNumber) =>
    `${hotelRoot()}/Rooms/${normalizeRoom(roomNumber)}`,
  alertsCollection: () => `${hotelRoot()}/Alerts`,
  broadcastsCollection: () => `${hotelRoot()}/Broadcasts`,
  requestsCollection: () => `${hotelRoot()}/Requests`,
  roomsCollection: () => `${hotelRoot()}/Rooms`,
  menuCollection: () => `${hotelRoot()}/Menu`,
  emergencyContactsCollection: () => `${hotelRoot()}/Emergency_Contacts`,
  dailyAgendaCollection: () => `${hotelRoot()}/Daily_Agenda`,
  menuSettingsDoc: () => `${hotelRoot()}/Config/menuSettings`,
  liveOrdersCollection: () => 'Live_Orders',
  usersCollection: () => 'users',
  /** Helper matching TenantManager.pathFor */
  tenantPath: (sub) => TenantManager.pathFor(sub),
};

export function logFirestoreWrite(label, docPath, payload) {
  console.log(`[Firestore WRITE] ${label}`);
  console.log(`  Path: ${docPath}`);
  console.log('  Data:', payload);
  console.log(
    `  TV listens: ${HOTELS_ROOT}/${normalizeHotelId(getHotelId())}/Rooms/{room} + ${HOTELS_ROOT}/${normalizeHotelId(getHotelId())}/Alerts?roomNumber={room}`,
  );
}

export function logFirestoreListen(label, path) {
  console.log(`[Firestore LISTEN] ${label} → ${path}`);
}

console.log(
  `[Firestore PATHS] Multi-tenant ready — root "${HOTELS_ROOT}/{hotelId}/…" (canonical HOTEL_ID=${DEFAULT_HOTEL_ID})`,
);
