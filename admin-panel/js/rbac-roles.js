/**
 * Staff role constants + normalization (no auth imports — safe for auth.js).
 */

export const STAFF_ROLES = Object.freeze([
  'admin',
  'kitchen',
  'reception',
  'housekeeping',
]);

const ROLE_ALIASES = Object.freeze({
  admin: 'admin',
  hotel_admin: 'admin',
  property_admin: 'admin',
  kitchen: 'kitchen',
  kds: 'kitchen',
  chef: 'kitchen',
  reception: 'reception',
  receptionist: 'reception',
  front_desk: 'reception',
  frontdesk: 'reception',
  housekeeping: 'housekeeping',
  hk: 'housekeeping',
  cleaner: 'housekeeping',
});

/**
 * Normalize free-form role strings from RTDB / Firestore into a staff role.
 * @param {unknown} raw
 * @returns {'admin'|'kitchen'|'reception'|'housekeeping'|null}
 */
export function normalizeStaffRole(raw) {
  if (raw == null || raw === '') return null;
  const key = String(raw).trim().toLowerCase().replace(/[\s-]+/g, '_');
  return ROLE_ALIASES[key] || (STAFF_ROLES.includes(key) ? key : null);
}

export function roleLabel(role) {
  const labels = {
    admin: 'Admin',
    kitchen: 'Kitchen',
    reception: 'Reception',
    housekeeping: 'Housekeeping',
  };
  return labels[role] || role || 'Staff';
}
