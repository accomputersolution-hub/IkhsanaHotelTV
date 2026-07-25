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
import { normalizeRoom, logFirestoreWrite } from './paths.js';
import { writeRoomAlert } from './alerts.js';

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

let knownOrderIds = new Set();
let ordersInitialized = false;
let currentFilter = 'all';
let allOrders = [];

export function initOrders() {
  setupFilterTabs();

  const q = query(collection(db, 'Live_Orders'), orderBy('timestamp', 'desc'));

  onSnapshot(
    q,
    (snapshot) => {
      hideConnectionError();
      allOrders = snapshot.docs.map((d) => ({ id: d.id, ...d.data() }));

      snapshot.docChanges().forEach((change) => {
        if (change.type === 'added') {
          if (ordersInitialized && !knownOrderIds.has(change.doc.id)) {
            playOrderBell();
            const banner = document.getElementById('new-order-banner');
            const room = change.doc.data()?.roomNumber || '?';
            banner.innerHTML = `🔔 New order from Room <strong>${room}</strong>!`;
            banner.classList.remove('hidden');
            setTimeout(() => banner.classList.add('hidden'), 6000);
          }
          knownOrderIds.add(change.doc.id);
        }
      });

      ordersInitialized = true;
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

function renderOrders() {
  const container = document.getElementById('orders-list');
  const badge = document.getElementById('orders-count');
  const orders = getFilteredOrders();

  const pending = allOrders.filter((o) => o.status !== 'delivered').length;
  badge.textContent = pending;
  badge.classList.toggle('has-pending', pending > 0);

  if (!orders.length) {
    container.innerHTML = `
      <div class="col-span-full empty-state">
        <p>${allOrders.length ? 'No orders match this filter' : 'No orders yet'}</p>
        <p class="text-sm mt-1">Incoming room orders will appear here in real time.</p>
      </div>`;
    return;
  }

  container.innerHTML = orders
    .map((order) => {
      const status = order.status || 'pending';
      const next = nextStatus(status);
      return `
      <div class="order-card" data-searchable data-search-text="room ${escapeHtml(String(order.roomNumber || ''))} ${escapeHtml(order.guestName || '')} ${escapeHtml(formatItems(order.items))}">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div class="flex items-center gap-3 flex-wrap">
              <span class="order-room">Room ${escapeHtml(String(order.roomNumber || '—'))}</span>
              <span class="${STATUS_STYLES[status] || STATUS_STYLES.pending}">
                ${STATUS_LABELS[status] || status}
              </span>
            </div>
            <p class="order-meta">${escapeHtml(order.guestName || 'Guest')} · ${formatTime(order.timestamp)}</p>
          </div>
          <p class="order-price">₹${(order.totalAmount || 0).toFixed(0)}</p>
        </div>
        <p class="order-items-box">${escapeHtml(formatItems(order.items))}</p>
        ${
          next
            ? `<button data-action="advance" data-id="${order.id}" data-next="${next}" class="kds-advance-btn">
                Mark as ${STATUS_LABELS[next]}
              </button>`
            : `<p class="kds-delivered-label">✓ Delivered</p>`
        }
      </div>`;
    })
    .join('');

  container.querySelectorAll('[data-action="advance"]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const id = btn.dataset.id;
      const nextSt = btn.dataset.next;
      const order = allOrders.find((o) => o.id === id);
      btn.disabled = true;
      btn.textContent = 'Updating…';
      try {
        await updateDoc(doc(db, 'Live_Orders', id), { status: nextSt });
        logFirestoreWrite('Order Status', `Live_Orders/${id}`, { status: nextSt });

        if (nextSt === 'preparing' || nextSt === 'delivered') {
          await sendOrderStatusAlert(order, nextSt);
          toast(`Order marked as ${STATUS_LABELS[nextSt]} — guest notified on TV`);
        } else {
          toast(`Order marked as ${STATUS_LABELS[nextSt]}`);
        }
      } catch (err) {
        toast('Failed to update order', 'error');
        console.error('[Firestore ERROR] Order status update failed:', err);
        btn.disabled = false;
        btn.textContent = `Mark as ${STATUS_LABELS[nextSt]}`;
      }
    });
  });
}
