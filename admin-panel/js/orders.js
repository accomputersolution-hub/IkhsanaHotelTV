import { db } from './firebase-config.js';
import {
  collection,
  doc,
  updateDoc,
  onSnapshot,
  query,
  orderBy,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import {
  STATUS_LABELS,
  STATUS_STYLES,
  nextStatus,
  formatTime,
  formatItems,
  escapeHtml,
  toast,
  playOrderBell,
  hideConnectionError,
  showConnectionError,
} from './utils.js';
import { normalizeRoom, formatRoomLabel, logFirestoreWrite } from './paths.js';
import { writeRoomAlert } from './alerts.js';
import {
  getHotelId,
  onHotelChange,
  onHotelMetaChange,
  isCorporateProperty,
} from './tenant-context.js';

const ORDER_STATUS_ALERTS = {
  preparing: {
    title: 'Order Update',
    message: 'Order Update: Your order is now being prepared!',
  },
  delivered: {
    title: 'Order Update',
    message: 'Order Update: Your order is on the way/delivered.',
  },
};

/** Hotel KDS status labels (unchanged). */
const HOTEL_STATUS_LABELS = {
  pending: 'Pending',
  preparing: 'Cooking',
  delivered: 'Ready',
};

/** Corporate pantry status labels. */
const CORPORATE_STATUS_LABELS = {
  pending: 'Pending',
  preparing: 'Preparing',
  delivered: 'Served',
};

const HOTEL_FILTER_LABELS = {
  pending: 'Pending',
  preparing: 'Cooking',
  delivered: 'Ready',
};

const CORPORATE_FILTER_LABELS = {
  pending: 'Pending',
  preparing: 'Preparing',
  delivered: 'Served',
};

let knownOrderIds = new Set();
let ordersInitialized = false;
let currentFilter = 'all';
let allOrders = [];
let ordersUnsub = null;
/** Pending pantry / KDS tickets for the active hotel (sidebar badge). */
let pendingPantryCount = 0;

export function initOrders() {
  setupFilterTabs();
  onHotelChange(() => {
    knownOrderIds = new Set();
    ordersInitialized = false;
    applyKdsChrome();
    listenOrders();
  });
  onHotelMetaChange(() => {
    applyKdsChrome();
    renderOrders();
  });
}

function statusLabels() {
  return isCorporateProperty() ? CORPORATE_STATUS_LABELS : HOTEL_STATUS_LABELS;
}

function labelForStatus(status) {
  const labels = statusLabels();
  return labels[status] || STATUS_LABELS[status] || status;
}

function roomLabel(roomNumber) {
  return escapeHtml(
    formatRoomLabel(roomNumber, { corporate: isCorporateProperty() }),
  );
}

/** Update KDS page title, filters, and top module title for property type. */
function applyKdsChrome() {
  const corporate = isCorporateProperty();
  const titleEl = document.getElementById('kds-page-title');
  const moduleTitleEl = document.getElementById('module-title');
  const kdsView = document.querySelector('[data-module-view="kds"]');
  const kdsNav = document.querySelector('[data-module="kds"] .nav-label');

  if (titleEl) {
    titleEl.textContent = corporate ? 'Pantry Requests' : 'Kitchen Display System';
  }
  if (kdsNav) {
    kdsNav.textContent = corporate ? 'Pantry Requests' : 'Kitchen KDS';
  }
  if (moduleTitleEl && kdsView && !kdsView.classList.contains('hidden')) {
    moduleTitleEl.textContent = corporate ? 'Pantry Requests' : 'Kitchen Display System';
  }

  const filterLabels = corporate ? CORPORATE_FILTER_LABELS : HOTEL_FILTER_LABELS;
  document.querySelectorAll('[data-kds-filter-label]').forEach((tab) => {
    const key = tab.dataset.kdsFilterLabel;
    if (filterLabels[key]) tab.textContent = filterLabels[key];
  });
}

function listenOrders() {
  if (ordersUnsub) {
    ordersUnsub();
    ordersUnsub = null;
  }

  const hotelId = getHotelId();
  if (!hotelId) {
    allOrders = [];
    updatePantryNavBadge();
    renderOrders();
    return;
  }

  const q = query(collection(db, 'Live_Orders'), orderBy('timestamp', 'desc'));

  ordersUnsub = onSnapshot(
    q,
    (snapshot) => {
      hideConnectionError();
      const currentHotelId = getHotelId();
      allOrders = snapshot.docs
        .map((d) => ({ id: d.id, ...d.data() }))
        .filter((o) => !o.hotelId || o.hotelId === currentHotelId);

      const hotelOrderIds = new Set(allOrders.map((o) => o.id));

      snapshot.docChanges().forEach((change) => {
        if (change.type === 'added') {
          const data = change.doc.data();
          if (data.hotelId !== currentHotelId) return;
          if (ordersInitialized && !knownOrderIds.has(change.doc.id)) {
            playOrderBell();
            const banner = document.getElementById('new-order-banner');
            const room = data?.roomNumber || '?';
            const place = isCorporateProperty()
              ? `Conf Room <strong>${escapeHtml(String(room))}</strong>`
              : `Room <strong>${escapeHtml(String(room))}</strong>`;
            const kind = isCorporateProperty() ? 'pantry request' : 'order';
            banner.innerHTML = `New ${kind} from ${place} — ticket created`;
            banner.classList.remove('hidden');
            setTimeout(() => banner.classList.add('hidden'), 6000);
          }
          knownOrderIds.add(change.doc.id);
        }
      });

      // Drop ids from other hotels so re-subscribe doesn't false-bell
      knownOrderIds = new Set([...knownOrderIds].filter((id) => hotelOrderIds.has(id)));

      ordersInitialized = true;
      updatePantryNavBadge();
      renderOrders();
    },
    (err) => {
      console.error('Orders listener error:', err);
      showConnectionError(
        'Could not connect to Firestore. Check your Firebase config and security rules.',
      );
    },
  );
}

function setupFilterTabs() {
  document.querySelectorAll('.filter-tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.filter-tab').forEach((t) => t.classList.remove('active'));
      tab.classList.add('active');
      currentFilter = tab.dataset.filter;
      renderOrders();
    });
  });
}

function getFilteredOrders() {
  if (currentFilter === 'all') return allOrders;
  return allOrders.filter((o) => (o.status || 'pending') === currentFilter);
}

async function sendOrderStatusAlert(order, newStatus) {
  const alertConfig = ORDER_STATUS_ALERTS[newStatus];
  if (!alertConfig || !order?.roomNumber) return;

  await writeRoomAlert({
    roomNumber: normalizeRoom(order.roomNumber),
    title: alertConfig.title,
    message: alertConfig.message,
    priority: 'normal',
    source: 'order_status',
    orderId: order.id,
  });
}

function isPendingOrder(order) {
  const status = String(order?.status || 'pending').toLowerCase().trim();
  return status === 'pending';
}

function updatePantryNavBadge() {
  pendingPantryCount = allOrders.filter(isPendingOrder).length;
  const badge = document.getElementById('nav-kds-badge');
  if (!badge) return;

  if (pendingPantryCount > 0) {
    badge.textContent = pendingPantryCount > 99 ? '99+' : String(pendingPantryCount);
    badge.hidden = false;
    badge.classList.remove('hidden');
    badge.setAttribute(
      'aria-label',
      `${pendingPantryCount} pending ${isCorporateProperty() ? 'pantry requests' : 'kitchen orders'}`,
    );
  } else {
    badge.textContent = '0';
    badge.hidden = true;
    badge.classList.add('hidden');
    badge.removeAttribute('aria-label');
  }
}

function renderOrders() {
  updatePantryNavBadge();
  const container = document.getElementById('orders-list');
  const badge = document.getElementById('orders-count');
  if (!container || !badge) return;

  const corporate = isCorporateProperty();
  const orders = getFilteredOrders();
  const labels = statusLabels();

  const pending = allOrders.filter((o) => o.status !== 'delivered').length;
  badge.textContent = pending;
  badge.classList.toggle('has-pending', pending > 0);

  if (!orders.length) {
    container.innerHTML = `
      <div class="col-span-full empty-state">
        <p>${allOrders.length ? 'No orders match this filter' : corporate ? 'No pantry requests yet' : 'No orders yet'}</p>
        <p class="text-sm mt-1">${
          corporate
            ? 'Incoming conference-room meal requests will appear here in real time.'
            : 'Incoming room orders will appear here in real time.'
        }</p>
      </div>`;
    return;
  }

  container.innerHTML = orders
    .map((order) => {
      const status = order.status || 'pending';
      const next = nextStatus(status);
      const priceHtml = corporate
        ? ''
        : `<p class="order-price">₹${(order.totalAmount || 0).toFixed(0)}</p>`;
      const doneLabel = corporate ? 'Served · Complete' : 'Ready · Complete';
      return `
      <div class="order-card" data-searchable data-search-text="room ${escapeHtml(String(order.roomNumber || ''))} ${escapeHtml(order.guestName || '')} ${escapeHtml(formatItems(order.items))}">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div class="flex items-center gap-3 flex-wrap">
              <span class="order-room">${roomLabel(order.roomNumber)}</span>
              <span class="${STATUS_STYLES[status] || STATUS_STYLES.pending}">
                ${escapeHtml(labels[status] || status)}
              </span>
            </div>
            <p class="order-meta">${escapeHtml(order.guestName || (corporate ? 'Staff' : 'Guest'))} · synced ${formatTime(order.timestamp)}</p>
          </div>
          ${priceHtml}
        </div>
        <p class="order-items-box">${escapeHtml(formatItems(order.items))}</p>
        ${
          next
            ? `<button data-action="advance" data-id="${order.id}" data-next="${next}" class="kds-advance-btn">
                Mark as ${escapeHtml(labels[next] || next)}
              </button>`
            : `<p class="kds-delivered-label">${doneLabel}</p>`
        }
      </div>`;
    })
    .join('');

  container.querySelectorAll('[data-action="advance"]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const id = btn.dataset.id;
      const nextSt = btn.dataset.next;
      const order = allOrders.find((o) => o.id === id);
      const nextLabel = labelForStatus(nextSt);
      btn.disabled = true;
      btn.textContent = 'Updating…';
      try {
        await updateDoc(doc(db, 'Live_Orders', id), { status: nextSt });
        logFirestoreWrite('Order Status', `Live_Orders/${id}`, { status: nextSt });

        if (nextSt === 'preparing' || nextSt === 'delivered') {
          await sendOrderStatusAlert(order, nextSt);
          toast(`Order marked as ${nextLabel} — guest notified on TV`);
        } else {
          toast(`Order marked as ${nextLabel}`);
        }
      } catch (err) {
        toast('Failed to update order', 'error');
        console.error('[Firestore ERROR] Order status update failed:', err);
        btn.disabled = false;
        btn.textContent = `Mark as ${nextLabel}`;
      }
    });
  });
}
