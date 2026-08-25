/** Sidebar navigation, module switching, clock, search */

import { onAnalyticsShown } from './analytics.js';
import { navigateTo } from './router.js';
import { isSuperAdmin } from './auth.js';
import { isCorporateProperty, onHotelMetaChange } from './tenant-context.js';
import { applyHelpdeskChrome } from './emergency-contacts.js';
import { applyAgendaChrome } from './daily-agenda.js';
import {
  applyRbacNavChrome,
  canAccessModule,
  resolveAllowedModule,
  getDefaultModuleForRole,
} from './rbac.js';

const MODULES = {
  pms: { label: 'Room Status', title: 'Room & Guest PMS' },
  'room-features': { label: 'Feature Toggles', title: 'Room Feature Toggles' },
  kds: { label: 'Food Orders', title: 'Kitchen Display System' },
  menu: { label: 'Menu Management', title: 'Digital Menu Configuration' },
  messaging: { label: 'TV Mass Messaging', title: 'TV Mass Messaging' },
  housekeeping: { label: 'Housekeeping', title: 'Housekeeping Queue' },
  agenda: { label: 'Daily Agenda', title: "Today's Agenda" },
  concierge: { label: 'Travel & Concierge', title: 'Travel & Concierge' },
  analytics: { label: 'Executive Analytics', title: 'Executive Analytics' },
  billing: { label: 'Billing', title: 'Billing' },
  staff: { label: 'Staff Management', title: 'Staff Management' },
};

/** Hotel-flavor-only sidebar modules — hidden when property_type === corporate. */
export const HOTEL_ONLY_MODULES = Object.freeze(['billing', 'concierge', 'analytics']);

function isHotelOnlyModule(id) {
  return HOTEL_ONLY_MODULES.includes(id);
}

function moduleTitle(id) {
  if (id === 'kds' && isCorporateProperty()) return 'Pantry Requests';
  if (id === 'housekeeping' && isCorporateProperty()) return 'Helpdesk Config';
  return MODULES[id]?.title || '';
}

let activeModule = localStorage.getItem('activeModule') || 'pms';

export function initNavigation() {
  // Always keep sidebar expanded (collapse feature removed)
  document.getElementById('sidebar')?.classList.remove('sidebar-collapsed');
  localStorage.removeItem('sidebarCollapsed');

  setupSidebarNav();
  setupClock();
  setupQuickActions();
  setupGlobalSearch();

  try {
    applyHelpdeskChrome?.();
  } catch (err) {
    console.error('[nav] helpdesk chrome failed', err);
  }
  try {
    applyAgendaChrome?.();
  } catch (err) {
    console.error('[nav] agenda chrome failed', err);
  }
  // RBAC first (may unhide by role), then corporate chrome re-hides hotel-only items.
  applyRbacNavChrome();
  applyCorporateNavChrome();

  try {
    if (activeModule === 'agenda' && !isCorporateProperty()) {
      activeModule = getDefaultModuleForRole();
    }
    if (isCorporateProperty() && isHotelOnlyModule(activeModule)) {
      activeModule = getDefaultModuleForRole();
    }
    activeModule = resolveAllowedModule(activeModule);
    showModule(activeModule);
  } catch (err) {
    console.error('[nav] showModule failed', err);
    activeModule = getDefaultModuleForRole();
    try {
      showModule(activeModule);
    } catch (_) {
      /* ignore */
    }
  }

  onHotelMetaChange(() => {
    try {
      applyHelpdeskChrome?.();
    } catch (err) {
      console.error('[nav] helpdesk chrome failed', err);
    }
    try {
      applyAgendaChrome?.();
    } catch (err) {
      console.error('[nav] agenda chrome failed', err);
    }
    applyRbacNavChrome();
    applyCorporateNavChrome();
    try {
      if (activeModule === 'agenda' && !isCorporateProperty()) {
        showModule(getDefaultModuleForRole());
        navigateTo(`/pms/${getDefaultModuleForRole()}`);
        return;
      }
      if (isCorporateProperty() && isHotelOnlyModule(activeModule)) {
        showModule(getDefaultModuleForRole());
        navigateTo(`/pms/${getDefaultModuleForRole()}`);
        return;
      }
      if (!canAccessModule(activeModule)) {
        const next = getDefaultModuleForRole();
        showModule(next);
        navigateTo(`/pms/${next}`);
        return;
      }
      if (activeModule === 'kds' || activeModule === 'housekeeping') {
        const titleEl = document.getElementById('module-title');
        if (titleEl) titleEl.textContent = moduleTitle(activeModule);
      }
    } catch (err) {
      console.error('[nav] meta chrome update failed', err);
    }
  });
}

export function setHotelChromeVisible(visible) {
  document.getElementById('open-super-admin-link')?.classList.toggle('hidden', !(visible && isSuperAdmin()));
  document.getElementById('pms-impersonate-wrap')?.classList.toggle('hidden', !(visible && isSuperAdmin()));
}

function setupSidebarNav() {
  document.querySelectorAll('[data-module]').forEach((btn) => {
    btn.addEventListener('click', () => {
      showModule(btn.dataset.module);
      navigateTo(`/pms/${btn.dataset.module}`);
    });
  });
}

/**
 * Corporate flavor: hide hotel-only sidebar items
 * (Billing, Travel & Concierge, Executive Analytics).
 * Must run AFTER applyRbacNavChrome so role unhide does not win.
 */
export function applyCorporateNavChrome() {
  const corporate = isCorporateProperty();
  for (const id of HOTEL_ONLY_MODULES) {
    const btn =
      document.getElementById(`nav-${id}`) ||
      document.querySelector(`[data-module="${id}"]`);
    if (!btn) continue;
    btn.classList.toggle('hidden', corporate);
    if (corporate) btn.setAttribute('aria-hidden', 'true');
    else btn.removeAttribute('aria-hidden');
  }
}

export function showModule(id) {
  if (id === 'agenda' && !isCorporateProperty()) {
    id = getDefaultModuleForRole();
  }
  if (isCorporateProperty() && isHotelOnlyModule(id)) {
    id = getDefaultModuleForRole();
  }
  id = resolveAllowedModule(id);
  if (!MODULES[id]) return;
  if (!canAccessModule(id)) {
    id = getDefaultModuleForRole();
  }
  activeModule = id;
  localStorage.setItem('activeModule', id);

  document.querySelectorAll('[data-module-view]').forEach((el) => {
    el.classList.toggle('hidden', el.dataset.moduleView !== id);
  });

  document.querySelectorAll('[data-module]').forEach((btn) => {
    const isActive = btn.dataset.module === id;
    btn.classList.toggle('nav-item-active', isActive);
    btn.setAttribute('aria-current', isActive ? 'page' : 'false');
  });

  const titleEl = document.getElementById('module-title');
  if (titleEl) titleEl.textContent = moduleTitle(id);

  if (id === 'analytics') onAnalyticsShown();

  applyGlobalSearch(document.getElementById('global-search')?.value || '');
}

function setupClock() {
  const clockEl = document.getElementById('live-clock');
  const dateEl = document.getElementById('live-date');
  if (!clockEl || !dateEl) return;

  const tick = () => {
    const now = new Date();
    clockEl.textContent = now.toLocaleTimeString('en-IN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
    dateEl.textContent = now.toLocaleDateString('en-IN', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
  };
  tick();
  setInterval(tick, 1000);
}

function setupQuickActions() {
  document.getElementById('bell-test-btn')?.addEventListener('click', () => {
    showModule('kds');
  });
}

function setupGlobalSearch() {
  const input = document.getElementById('global-search');
  input?.addEventListener('input', (e) => applyGlobalSearch(e.target.value.trim().toLowerCase()));
}

function applyGlobalSearch(query) {
  const view = document.querySelector(`[data-module-view="${activeModule}"]`);
  if (!view) return;

  view.querySelectorAll('[data-searchable]').forEach((el) => {
    if (!query) {
      el.classList.remove('search-hidden');
      return;
    }
    const text = (el.dataset.searchText || el.textContent || '').toLowerCase();
    el.classList.toggle('search-hidden', !text.includes(query));
  });
}
