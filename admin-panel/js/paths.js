import { HOTEL_ID } from './firebase-config.js';

/** Must stay in sync with FirestorePaths.kt in the Android TV app */
export function normalizeRoom(roomNumber) {
  return String(roomNumber ?? '').trim();
}

export const paths = {
  roomDoc: (roomNumber) =>
    `Hotels/${HOTEL_ID}/Rooms/${normalizeRoom(roomNumber)}`,
  alertsCollection: () => `Hotels/${HOTEL_ID}/Alerts`,
  broadcastsCollection: () => `Hotels/${HOTEL_ID}/Broadcasts`,
  requestsCollection: () => `Hotels/${HOTEL_ID}/Requests`,
  roomsCollection: () => `Hotels/${HOTEL_ID}/Rooms`,
  menuCollection: () => `Hotels/${HOTEL_ID}/Menu`,
  menuSettingsDoc: () => `Hotels/${HOTEL_ID}/Config/menuSettings`,
  liveOrdersCollection: () => 'Live_Orders',
};

export function logFirestoreWrite(label, docPath, payload) {
  console.log(`[Firestore WRITE] ${label}`);
  console.log(`  Path: ${docPath}`);
  console.log('  Data:', payload);
  console.log(`  TV listens: Hotels/${HOTEL_ID}/Rooms/{room} + Hotels/${HOTEL_ID}/Alerts?roomNumber={room}`);
}

export function logFirestoreListen(label, path) {
  console.log(`[Firestore LISTEN] ${label} → ${path}`);
}

console.log('[Firestore PATHS] Admin panel configured for:');
console.log(`  hotelId: ${HOTEL_ID}`);
console.log(`  Guest:  Hotels/${HOTEL_ID}/Rooms/{roomNumber}`);
console.log(`  Alerts: Hotels/${HOTEL_ID}/Alerts  (field: roomNumber)`);
console.log(`  Broadcasts: Hotels/${HOTEL_ID}/Broadcasts`);
console.log(`  Requests: Hotels/${HOTEL_ID}/Requests`);
console.log(`  Menu:   Hotels/${HOTEL_ID}/Menu`);
console.log(`  Orders: Live_Orders`);
console.log('  TV default room: 101 — admin must target the same room number');
