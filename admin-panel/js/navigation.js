/** Sidebar navigation, module switching, clock, search, collapse */

import { createTestRequest } from './requests.js';
import { onAnalyticsShown } from './analytics.js';
import { toast } from './utils.js';

const MODULES = {
  pms: { label: 'Room & Guest PMS', title: 'Room & Guest PMS' },
  kds: { label: 'Kitchen KDS', title: 'Kitchen Display System' },
  menu: { label: 'Digital Menu Config', title: 'Digital Menu Configuration' },
  messaging: { label: 'TV Mass Messaging', title: 'TV Mass Messaging' },
  housekeeping: { label: 'Housekeeping Queue', title: 'Housekeeping Queue' },
  concierge: { label: 'Travel & Concierge', title: 'Travel & Concierge' },
  analytics: { label: 'Executive Analytics', title: 'Executive Analytics' },
};

let activeModule = localStorage.getItem('activeModule') || 'pms';

export function initNavigation() {
  setupSidebarNav();
  setupSidebarCollapse();
  setupClock();
  setupQuickActions();
  setupGlobalSearch();
  showModule(activeModule);
}

function setupSidebarNav() {
  document.querySelectorAll('[data-module]').forEach((btn) => {
    btn.addEventListener('click', () => {
      showModule(btn.dataset.module);
    });
  });
}

function showModule(id) {
  if (!MODULES[id]) return;
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
  if (titleEl) titleEl.textContent = MODULES[id].title;

  if (id === 'analytics') onAnalyticsShown();

  applyGlobalSearch(document.getElementById('global-search')?.value || '');
}

function setupSidebarCollapse() {
  const sidebar = document.getElementById('sidebar');
  const toggle = document.getElementById('sidebar-toggle');
  const collapsed = localStorage.getItem('sidebarCollapsed') === 'true';

  if (collapsed) sidebar?.classList.add('sidebar-collapsed');

  toggle?.addEventListener('click', () => {
    sidebar?.classList.toggle('sidebar-collapsed');
    const collapsed = sidebar?.classList.contains('sidebar-collapsed');
    localStorage.setItem('sidebarCollapsed', collapsed ? 'true' : 'false');
    if (toggle) toggle.textContent = collapsed ? '▶' : '◀ Collapse';
  });

  if (collapsed && toggle) toggle.textContent = '▶';
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
  document.getElementById('sim-order-btn')?.addEventListener('click', () => {
    showModule('kds');
    document.getElementById('new-order-banner')?.classList.remove('hidden');
    setTimeout(() => document.getElementById('new-order-banner')?.classList.add('hidden'), 4000);
  });

  document.getElementById('sim-request-btn')?.addEventListener('click', async () => {
    const dept = activeModule === 'concierge' ? 'concierge' : 'housekeeping';
    showModule(dept);
    try {
      await createTestRequest(dept);
      toast(`Simulated ${dept} request created`);
    } catch (err) {
      toast('Failed to create test request', 'error');
      console.error(err);
    }
  });

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

export { showModule };
