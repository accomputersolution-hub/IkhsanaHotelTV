import { db, HOTEL_ID } from './firebase-config.js';
import {
  collection,
  onSnapshot,
  query,
  orderBy,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import { paths, logFirestoreListen } from './paths.js';
import { hideConnectionError, showConnectionError } from './utils.js';

/** Nightly rack rates — used for today's room revenue estimate */
const ROOM_RATES = {
  deluxe: 4500,
  suite: 8500,
};

const CHART_COLORS = {
  gold: '#C9A962',
  goldLight: '#E8D5A3',
  cyan: '#22D3EE',
  emerald: '#34D399',
  amber: '#FBBF24',
  rose: '#FB7185',
  grid: 'rgba(148, 163, 184, 0.12)',
  text: '#94A3B8',
};

const state = {
  rooms: [],
  orders: [],
  requests: [],
};

const charts = {
  category: null,
  trends: null,
};

export function initAnalytics() {
  listenRooms();
  listenOrders();
  listenRequests();
}

/** Call when analytics module becomes visible — fixes Chart.js zero-size canvas issue */
export function onAnalyticsShown() {
  requestAnimationFrame(() => {
    updateCategoryChart();
    updateTrendsChart();
    if (charts.category) charts.category.resize();
    if (charts.trends) charts.trends.resize();
  });
}

function listenRooms() {
  logFirestoreListen('Analytics Rooms', paths.roomsCollection());
  onSnapshot(
    collection(db, 'Hotels', HOTEL_ID, 'Rooms'),
    (snapshot) => {
      hideConnectionError();
      state.rooms = snapshot.docs.map((d) => ({ id: d.id, ...d.data() }));
      onDataUpdate();
    },
    (err) => {
      console.error('[Firestore ERROR] Analytics rooms:', err);
      showConnectionError('Could not load analytics room data.');
    },
  );
}

function listenOrders() {
  logFirestoreListen('Analytics Orders', paths.liveOrdersCollection());
  const q = query(collection(db, 'Live_Orders'), orderBy('timestamp', 'desc'));
  onSnapshot(
    q,
    (snapshot) => {
      hideConnectionError();
      state.orders = snapshot.docs
        .map((d) => ({ id: d.id, ...d.data() }))
        .filter((o) => !o.hotelId || o.hotelId === HOTEL_ID);
      onDataUpdate();
    },
    (err) => {
      console.error('[Firestore ERROR] Analytics orders:', err);
      showConnectionError('Could not load analytics order data.');
    },
  );
}

function listenRequests() {
  logFirestoreListen('Analytics Requests', paths.requestsCollection());
  onSnapshot(
    collection(db, 'Hotels', HOTEL_ID, 'Requests'),
    (snapshot) => {
      hideConnectionError();
      state.requests = snapshot.docs.map((d) => ({ id: d.id, ...d.data() }));
      onDataUpdate();
    },
    (err) => {
      console.error('[Firestore ERROR] Analytics requests:', err);
      showConnectionError('Could not load analytics request data.');
    },
  );
}

function onDataUpdate() {
  updateKpis();
  updateCategoryChart();
  updateTrendsChart();
  updateTopMenuItems();
  updateLastRefreshed();
}

function deriveStatus(room) {
  if (room.status === 'occupied') return 'occupied';
  if (room.status === 'housekeeping' || room.status === 'maintenance' || room.status === 'needs_cleaning') {
    return room.status;
  }
  if (room.status === 'vacant') return 'vacant';
  const name = room.guestName?.trim();
  if (name && name !== 'Guest') return 'occupied';
  return 'vacant';
}

function toMillis(ts) {
  if (!ts) return 0;
  if (typeof ts.toMillis === 'function') return ts.toMillis();
  if (typeof ts === 'number') return ts;
  const d = new Date(ts);
  return Number.isNaN(d.getTime()) ? 0 : d.getTime();
}

function isToday(ts) {
  const ms = toMillis(ts);
  if (!ms) return false;
  const d = new Date(ms);
  const today = new Date();
  return d.toDateString() === today.toDateString();
}

function formatCurrency(amount) {
  if (amount >= 100000) return `₹${(amount / 100000).toFixed(1)}L`;
  if (amount >= 1000) return `₹${(amount / 1000).toFixed(1)}k`;
  return `₹${Math.round(amount)}`;
}

function formatCurrencyFull(amount) {
  return `₹${Math.round(amount).toLocaleString('en-IN')}`;
}

function computeMetrics() {
  const { rooms, orders, requests } = state;

  const totalRooms = rooms.length;
  const occupiedRooms = rooms.filter((r) => deriveStatus(r) === 'occupied');
  const occupancyRate = totalRooms ? Math.round((occupiedRooms.length / totalRooms) * 100) : 0;

  const todayOrders = orders.filter((o) => isToday(o.timestamp));
  const fbRevenue = todayOrders.reduce((sum, o) => sum + (Number(o.totalAmount) || 0), 0);
  const roomRevenue = occupiedRooms.reduce(
    (sum, r) => sum + (ROOM_RATES[r.roomType] || ROOM_RATES.deluxe),
    0,
  );
  const totalRevenue = fbRevenue + roomRevenue;

  const activeRequests = requests.filter(
    (r) => r.status === 'pending' || r.status === 'in_progress',
  ).length;

  const pendingHkRequests = requests.filter(
    (r) =>
      r.department === 'housekeeping' &&
      (r.status === 'pending' || r.status === 'in_progress'),
  ).length;
  const pendingHkRooms = rooms.filter(
    (r) => r.status === 'housekeeping' || r.status === 'needs_cleaning',
  ).length;

  const itemCounts = {};
  orders.forEach((order) => {
    (order.items || []).forEach((item) => {
      const name = item.name || 'Unknown Item';
      itemCounts[name] = (itemCounts[name] || 0) + (Number(item.quantity) || 1);
    });
  });
  const topItems = Object.entries(itemCounts)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 8);

  const trendDays = [];
  for (let i = 6; i >= 0; i -= 1) {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    d.setDate(d.getDate() - i);
    trendDays.push(d);
  }

  const trendLabels = trendDays.map((d) =>
    d.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric' }),
  );
  const trendCounts = trendDays.map((d) => {
    const key = d.toDateString();
    return orders.filter((o) => {
      const ms = toMillis(o.timestamp);
      return ms && new Date(ms).toDateString() === key;
    }).length;
  });
  const trendRevenue = trendDays.map((d) => {
    const key = d.toDateString();
    return orders
      .filter((o) => {
        const ms = toMillis(o.timestamp);
        return ms && new Date(ms).toDateString() === key;
      })
      .reduce((sum, o) => sum + (Number(o.totalAmount) || 0), 0);
  });

  return {
    totalRooms,
    occupiedCount: occupiedRooms.length,
    occupancyRate,
    fbRevenue,
    roomRevenue,
    totalRevenue,
    todayOrderCount: todayOrders.length,
    activeRequests,
    pendingHkRequests,
    pendingHkRooms,
    topItems,
    trendLabels,
    trendCounts,
    trendRevenue,
  };
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

function updateKpis() {
  const m = computeMetrics();

  setText('ana-kpi-occupancy', `${m.occupancyRate}%`);
  setText('ana-kpi-occupancy-sub', `${m.occupiedCount} of ${m.totalRooms} rooms occupied`);

  setText('ana-kpi-revenue', formatCurrency(m.totalRevenue));
  setText(
    'ana-kpi-revenue-sub',
    `F&B ${formatCurrency(m.fbRevenue)} · Rooms ${formatCurrency(m.roomRevenue)}`,
  );
  const revenueEl = document.getElementById('ana-kpi-revenue');
  if (revenueEl) revenueEl.title = formatCurrencyFull(m.totalRevenue);

  setText('ana-kpi-requests', String(m.activeRequests));
  setText('ana-kpi-requests-sub', 'Pending & in-progress service requests');

  setText('ana-kpi-housekeeping', String(m.pendingHkRequests + m.pendingHkRooms));
  setText(
    'ana-kpi-housekeeping-sub',
    `${m.pendingHkRequests} guest req · ${m.pendingHkRooms} rooms awaiting clean`,
  );

  setText('ana-today-orders', String(m.todayOrderCount));
}

function chartDefaults() {
  if (typeof Chart === 'undefined') return;
  Chart.defaults.color = CHART_COLORS.text;
  Chart.defaults.borderColor = CHART_COLORS.grid;
  Chart.defaults.font.family = "'Inter', system-ui, sans-serif";
}

function updateCategoryChart() {
  if (typeof Chart === 'undefined') return;
  chartDefaults();

  const m = computeMetrics();
  const canvas = document.getElementById('ana-category-chart');
  if (!canvas) return;

  const data = {
    labels: ['Food & Beverage', 'Room Stays'],
    datasets: [
      {
        data: [m.fbRevenue, m.roomRevenue],
        backgroundColor: [
          'rgba(34, 211, 238, 0.75)',
          'rgba(201, 169, 98, 0.85)',
        ],
        borderColor: [CHART_COLORS.cyan, CHART_COLORS.gold],
        borderWidth: 2,
        hoverOffset: 8,
      },
    ],
  };

  if (charts.category) {
    charts.category.data = data;
    charts.category.update('none');
    return;
  }

  charts.category = new Chart(canvas, {
    type: 'doughnut',
    data,
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '62%',
      plugins: {
        legend: {
          position: 'bottom',
          labels: { padding: 16, usePointStyle: true, pointStyle: 'circle' },
        },
        tooltip: {
          callbacks: {
            label(ctx) {
              const val = ctx.parsed || 0;
              const total = m.totalRevenue || 1;
              const pct = Math.round((val / total) * 100);
              return ` ${ctx.label}: ${formatCurrencyFull(val)} (${pct}%)`;
            },
          },
        },
      },
    },
  });
}

function updateTrendsChart() {
  if (typeof Chart === 'undefined') return;
  chartDefaults();

  const m = computeMetrics();
  const canvas = document.getElementById('ana-trends-chart');
  if (!canvas) return;

  const data = {
    labels: m.trendLabels,
    datasets: [
      {
        label: 'Orders',
        data: m.trendCounts,
        borderColor: CHART_COLORS.cyan,
        backgroundColor: 'rgba(34, 211, 238, 0.12)',
        fill: true,
        tension: 0.35,
        yAxisID: 'y',
        pointRadius: 4,
        pointHoverRadius: 6,
        pointBackgroundColor: CHART_COLORS.cyan,
      },
      {
        label: 'F&B Revenue (₹)',
        data: m.trendRevenue,
        borderColor: CHART_COLORS.gold,
        backgroundColor: 'rgba(201, 169, 98, 0.08)',
        fill: true,
        tension: 0.35,
        yAxisID: 'y1',
        pointRadius: 4,
        pointHoverRadius: 6,
        pointBackgroundColor: CHART_COLORS.gold,
      },
    ],
  };

  if (charts.trends) {
    charts.trends.data = data;
    charts.trends.update('none');
    return;
  }

  charts.trends = new Chart(canvas, {
    type: 'line',
    data,
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: {
          position: 'top',
          align: 'end',
          labels: { usePointStyle: true, pointStyle: 'circle', padding: 12 },
        },
        tooltip: {
          callbacks: {
            label(ctx) {
              if (ctx.datasetIndex === 1) {
                return ` ${ctx.dataset.label}: ${formatCurrencyFull(ctx.parsed.y)}`;
              }
              return ` ${ctx.dataset.label}: ${ctx.parsed.y}`;
            },
          },
        },
      },
      scales: {
        x: {
          grid: { color: CHART_COLORS.grid },
          ticks: { maxRotation: 0 },
        },
        y: {
          position: 'left',
          grid: { color: CHART_COLORS.grid },
          ticks: { stepSize: 1 },
          title: { display: true, text: 'Orders', color: CHART_COLORS.cyan },
        },
        y1: {
          position: 'right',
          grid: { drawOnChartArea: false },
          ticks: {
            callback: (v) => formatCurrency(v),
          },
          title: { display: true, text: 'Revenue', color: CHART_COLORS.gold },
        },
      },
    },
  });
}

function updateTopMenuItems() {
  const container = document.getElementById('ana-top-items');
  if (!container) return;

  const m = computeMetrics();

  if (!m.topItems.length) {
    container.innerHTML = `
      <p class="empty-state">No menu orders yet. TV dining orders will appear here.</p>`;
    return;
  }

  const maxQty = m.topItems[0][1];

  container.innerHTML = m.topItems
    .map(([name, qty], index) => {
      const pct = maxQty ? Math.round((qty / maxQty) * 100) : 0;
      const rankClass = index === 0 ? 'top-item-rank-gold' : index === 1 ? 'top-item-rank-silver' : '';
      return `
        <div class="top-menu-item" data-searchable data-search-text="${escapeAttr(name)}">
          <div class="top-menu-item-header">
            <span class="top-item-rank ${rankClass}">#${index + 1}</span>
            <span class="top-item-name">${escapeHtml(name)}</span>
            <span class="top-item-qty">${qty} ordered</span>
          </div>
          <div class="top-item-bar-track">
            <div class="top-item-bar-fill" style="width:${pct}%"></div>
          </div>
        </div>`;
    })
    .join('');
}

function updateLastRefreshed() {
  const el = document.getElementById('ana-last-updated');
  if (!el) return;
  el.textContent = `Live · updated ${new Date().toLocaleTimeString('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })}`;
}

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function escapeAttr(str) {
  return escapeHtml(str).replace(/'/g, '&#39;');
}
