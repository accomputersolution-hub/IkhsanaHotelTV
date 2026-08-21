/**
 * Role-Based Access Control for hotel staff PMS.
 *
 * Staff roles live in Realtime Database:
 *   staff_users/{uid}/role  →  admin | kitchen | reception | housekeeping
 *
 * Platform roles (Firestore users/{uid}.role) stay separate:
 *   super_admin | hotel_admin
 *
 * hotel_admin without a staff_users entry defaults to full "admin" module access
 * (backward compatible with existing property accounts).
 */

import { getCurrentProfile, isSuperAdmin } from './auth.js';
import {
  STAFF_ROLES,
  normalizeStaffRole,
  roleLabel,
} from './rbac-roles.js';

export { STAFF_ROLES, normalizeStaffRole, roleLabel };

/**
 * Modules each staff role may open.
 * Keys match `data-module` / router module ids.
 *
 * Mapping (product language → module id):
 *   Food Orders      → kds
 *   Menu Management  → menu
 *   Room Status      → pms
 *   Billing          → billing
 *   Housekeeping     → housekeeping
 *   Staff Management → staff
 */
export const ROLE_MODULES = Object.freeze({
  admin: [
    'pms',
    'kds',
    'menu',
    'messaging',
    'housekeeping',
    'agenda',
    'concierge',
    'analytics',
    'billing',
    'staff',
  ],
  kitchen: ['kds', 'menu'],
  reception: ['pms', 'billing', 'messaging', 'concierge'],
  housekeeping: ['housekeeping', 'pms'],
});

/** Default landing module after login, per staff role. */
export const ROLE_DEFAULT_MODULE = Object.freeze({
  admin: 'pms',
  kitchen: 'kds',
  reception: 'pms',
  housekeeping: 'housekeeping',
});

/**
 * Operational role used for module gates.
 * Super Admin impersonating a property is treated as full admin.
 */
export function getOperationalRole(profile = getCurrentProfile()) {
  if (!profile) return null;
  if (profile.role === 'super_admin') return 'admin';
  const fromStaff = normalizeStaffRole(profile.staffRole);
  if (fromStaff) return fromStaff;
  if (profile.role === 'hotel_admin') return 'admin';
  return null;
}

/**
 * Permission helper — modular gate for roles and modules.
 *
 * @example
 *   hasAccess('kitchen', 'kds')           // true — module
 *   hasAccess('kitchen', 'pms')           // false
 *   hasAccess('admin', 'staff')           // true
 *   hasAccess('reception', 'kitchen')     // false — only matching role (admin passes all)
 *
 * @param {string|null|undefined} userRole
 * @param {string} required  Module id OR staff role name
 * @returns {boolean}
 */
export function hasAccess(userRole, required) {
  if (!required) return false;
  const role = normalizeStaffRole(userRole);
  if (!role) return false;
  if (role === 'admin') return true;

  const need = String(required).trim().toLowerCase();
  const requiredRole = normalizeStaffRole(need);
  const knownModules = new Set(Object.values(ROLE_MODULES).flat());

  // Role-vs-role (e.g. hasAccess('kitchen', 'kitchen')) — not a module id
  if (requiredRole && !knownModules.has(need)) {
    return role === requiredRole;
  }

  return (ROLE_MODULES[role] || []).includes(need);
}

/** True if this signed-in profile may open the property PMS shell. */
export function canAccessPropertyPms(profile = getCurrentProfile()) {
  if (!profile) return false;
  if (profile.role === 'hotel_admin' && profile.hotelId) return true;
  if (normalizeStaffRole(profile.staffRole) && profile.hotelId) return true;
  return false;
}

/** Modules the current user may open (empty if unknown). */
export function getAccessibleModules(profile = getCurrentProfile()) {
  if (isSuperAdmin() && profile?.role === 'super_admin') {
    return [...ROLE_MODULES.admin];
  }
  const role = getOperationalRole(profile);
  if (!role) return [];
  return [...(ROLE_MODULES[role] || [])];
}

export function canAccessModule(moduleId, profile = getCurrentProfile()) {
  if (isSuperAdmin() && profile?.role === 'super_admin') return true;
  return hasAccess(getOperationalRole(profile), moduleId);
}

export function getDefaultModuleForRole(profile = getCurrentProfile()) {
  const role = getOperationalRole(profile);
  if (!role) return 'pms';
  const preferred = ROLE_DEFAULT_MODULE[role] || 'pms';
  if (canAccessModule(preferred, profile)) return preferred;
  return getAccessibleModules(profile)[0] || 'pms';
}

/**
 * Show/hide sidebar nav items from the current operational role.
 * Compose with corporate chrome (call after applyCorporateNavChrome).
 */
export function applyRbacNavChrome() {
  const allowed = new Set(getAccessibleModules());
  document.querySelectorAll('[data-module]').forEach((btn) => {
    const id = btn.dataset.module;
    if (!id) return;
    const blockedByRole = !allowed.has(id);
    if (blockedByRole) {
      btn.classList.add('hidden');
      btn.setAttribute('aria-hidden', 'true');
      btn.disabled = true;
      return;
    }
    btn.disabled = false;
    btn.removeAttribute('aria-hidden');
    // agenda visibility remains owned by applyAgendaChrome — do not force-show
    if (id !== 'agenda') {
      btn.classList.remove('hidden');
    }
  });

  const badge = document.getElementById('staff-role-badge');
  const role = getOperationalRole();
  if (badge) {
    if (role) {
      badge.textContent = roleLabel(role);
      badge.classList.remove('hidden');
    } else {
      badge.classList.add('hidden');
    }
  }
}

/** Pick a safe module if the requested one is forbidden. */
export function resolveAllowedModule(requestedId, profile = getCurrentProfile()) {
  if (requestedId && canAccessModule(requestedId, profile)) return requestedId;
  return getDefaultModuleForRole(profile);
}
