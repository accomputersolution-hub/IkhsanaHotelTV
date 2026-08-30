import { getHotelId } from './tenant-context.js';
import { TenantManager } from './tenant-context.js';
import { DEFAULT_HOTEL_ID, normalizeHotelId } from './firebase-config.js';

export { normalizeHotelId, DEFAULT_HOTEL_ID };

/** Must stay in sync with FirestorePaths.kt — capital "Hotels" only */
export const HOTELS_ROOT = 'Hotels';

export function normalizeRoom(roomNumber) {
  return String(roomNumber ?? '').trim();
}

/**
 * True when the room id is non-empty and entirely digits (e.g. "101").
 * Named rooms like "Middle East" return false.
 */
export function isNumericRoomId(roomNumber) {
  const s = normalizeRoom(roomNumber);
  return s.length > 0 && /^\d+$/.test(s);
}

/**
 * Guest/admin-facing label:
 * - digits only → "Room 101" (or "Conf Room 101" when corporate=true)
 * - any letters → exact name, no prefix
 */
export function formatRoomLabel(roomNumber, { corporate = false } = {}) {
  const s = normalizeRoom(roomNumber);
  if (!s) return '—';
  if (!isNumericRoomId(s)) return s;
  return corporate ? `Conf Room ${s}` : `Room ${s}`;
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
  /** Intro / splash video for TV cold start — Hotels/{id}/Config/intro */
  introConfigDoc: () => `${hotelRoot()}/Config/intro`,
  liveOrdersCollection: () => 'Live_Orders',
  usersCollection: () => 'users',
  /** Helper matching TenantManager.pathFor */
  tenantPath: (sub) => TenantManager.pathFor(sub),
  /** RTDB TV ticker — hotels/{hotelId}/config/global_announcement */
  rtdbAnnouncement: (hotelId = getHotelId()) =>
    `hotels/${normalizeHotelId(hotelId)}/config/global_announcement`,
  /** RTDB staff RBAC — staff_users/{uid}/role */
  rtdbStaffUser: (uid) => `staff_users/${uid}`,
  rtdbStaffUsers: () => 'staff_users',
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
