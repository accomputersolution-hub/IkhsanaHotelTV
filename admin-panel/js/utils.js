/** Shared utilities: toast, bell audio, formatters, connection status */

let audioCtx = null;
let soundEnabled = localStorage.getItem('orderBellEnabled') !== 'false';

export function isSoundEnabled() {
  return soundEnabled;
}

export function setSoundEnabled(enabled) {
  soundEnabled = enabled;
  localStorage.setItem('orderBellEnabled', enabled ? 'true' : 'false');
  updateSoundToggleUi();
}

export function initAudio() {
  const unlock = () => {
    try {
      if (!audioCtx) {
        audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      }
      audioCtx.resume();
    } catch (_) {}
  };

  document.body.addEventListener('click', unlock, { once: true });
  document.body.addEventListener('keydown', unlock, { once: true });

  setupSoundToggle();
  updateSoundToggleUi();
}

function setupSoundToggle() {
  document.getElementById('bell-test-btn')?.addEventListener('click', () => {
    unlockAudio();
    playOrderBell({ force: true });
    toast('Bell test played');
  });

  document.getElementById('bell-toggle-btn')?.addEventListener('click', () => {
    setSoundEnabled(!soundEnabled);
    toast(soundEnabled ? 'Order bell enabled' : 'Order bell muted');
    if (soundEnabled) {
      unlockAudio();
      playOrderBell({ force: true });
    }
  });
}

function updateSoundToggleUi() {
  const btn = document.getElementById('bell-toggle-btn');
  const icon = document.getElementById('bell-toggle-icon');
  if (!btn || !icon) return;
  if (soundEnabled) {
    icon.textContent = '🔔';
    btn.title = 'Mute order bell';
    btn.classList.remove('opacity-50');
  } else {
    icon.textContent = '🔕';
    btn.title = 'Enable order bell';
    btn.classList.add('opacity-50');
  }
}

function unlockAudio() {
  try {
    if (!audioCtx) {
      audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    }
    audioCtx.resume();
  } catch (_) {}
}

/** Soft chime for housekeeping / concierge requests */
export function playServiceChime({ force = false } = {}) {
  if (!force && !soundEnabled) return;

  try {
    unlockAudio();
    const ctx = audioCtx;
    if (!ctx) return;

    const now = ctx.currentTime;
    const notes = [
      { freq: 1046.5, gain: 0.22, delay: 0 },
      { freq: 1318.5, gain: 0.18, delay: 0.12 },
    ];

    notes.forEach(({ freq, gain, delay }) => {
      const osc = ctx.createOscillator();
      const amp = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.value = freq;
      osc.connect(amp);
      amp.connect(ctx.destination);

      const start = now + delay;
      amp.gain.setValueAtTime(0.0001, start);
      amp.gain.exponentialRampToValueAtTime(gain, start + 0.02);
      amp.gain.exponentialRampToValueAtTime(0.0001, start + 0.7);
      osc.start(start);
      osc.stop(start + 0.75);
    });
  } catch (_) {}
}

/**
 * Realistic front-desk bell: layered partials with rapid decay, rung twice.
 */
export function playOrderBell({ force = false } = {}) {
  if (!force && !soundEnabled) return;

  try {
    unlockAudio();
    const ctx = audioCtx;
    if (!ctx) return;

    const now = ctx.currentTime;
    const rings = [0, 0.55];

    rings.forEach((offset) => {
      const partials = [
        { freq: 830, gain: 0.45 },
        { freq: 1660, gain: 0.22 },
        { freq: 2490, gain: 0.1 },
        { freq: 1245, gain: 0.15 },
      ];

      partials.forEach(({ freq, gain }) => {
        const osc = ctx.createOscillator();
        const amp = ctx.createGain();
        osc.type = 'sine';
        osc.frequency.value = freq;
        osc.connect(amp);
        amp.connect(ctx.destination);

        const start = now + offset;
        amp.gain.setValueAtTime(0.0001, start);
        amp.gain.exponentialRampToValueAtTime(gain, start + 0.015);
        amp.gain.exponentialRampToValueAtTime(0.0001, start + 0.85);
        osc.start(start);
        osc.stop(start + 0.9);
      });
    });
  } catch (_) {}
}

/** @deprecated use playOrderBell */
export function playNewOrderSound() {
  playOrderBell();
}

export function formatTime(ts) {
  if (!ts) return '—';
  const date = ts.toDate ? ts.toDate() : new Date(ts);
  return date.toLocaleString('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
    day: 'numeric',
    month: 'short',
  });
}

export function formatItems(items) {
  if (!items || !items.length) return '—';
  return items.map((i) => `${i.quantity || 1}× ${i.name || 'Item'}`).join(', ');
}

export function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str ?? '';
  return div.innerHTML;
}

export function toast(message, type = 'success') {
  const el = document.getElementById('toast');
  el.textContent = message;
  el.className = `fixed bottom-6 right-6 z-50 px-5 py-3 rounded-xl shadow-lg text-sm font-medium transition-all duration-300 ${
    type === 'error' ? 'bg-red-600 text-white' : 'bg-emerald-600 text-white'
  }`;
  el.classList.remove('opacity-0', 'translate-y-4');
  setTimeout(() => el.classList.add('opacity-0', 'translate-y-4'), 3000);
}

export function setConnectionStatus(state) {
  const dot = document.getElementById('conn-dot');
  const label = document.getElementById('conn-label');
  const badge = document.getElementById('pms-badge');
  if (!dot || !label) return;

  dot.className = `conn-dot w-2 h-2 rounded-full ${state}`;
  const labels = {
    connected: 'Connected & Active',
    connecting: 'Syncing…',
    disconnected: 'Offline',
  };
  label.textContent = labels[state] || state;

  if (badge) {
    badge.classList.toggle('disconnected', state === 'disconnected');
  }
}

export function showConnectionError(message) {
  const el = document.getElementById('connection-error');
  if (el) {
    el.textContent = message;
    el.classList.remove('hidden');
  }
  setConnectionStatus('disconnected');
}

export function hideConnectionError() {
  document.getElementById('connection-error')?.classList.add('hidden');
  setConnectionStatus('connected');
}

export const STATUS_FLOW = ['pending', 'preparing', 'delivered'];
export const STATUS_LABELS = {
  pending: 'Pending',
  preparing: 'Preparing',
  delivered: 'Delivered',
};
export const STATUS_STYLES = {
  pending: 'status-badge status-pending',
  preparing: 'status-badge status-preparing',
  delivered: 'status-badge status-delivered',
};

export function nextStatus(current) {
  const idx = STATUS_FLOW.indexOf(current);
  return idx < STATUS_FLOW.length - 1 ? STATUS_FLOW[idx + 1] : null;
}

/** Open/close modal helpers */
export function openModal(id) {
  document.getElementById(id)?.classList.remove('hidden');
  document.body.classList.add('modal-open');
}

export function closeModal(id) {
  document.getElementById(id)?.classList.add('hidden');
  document.body.classList.remove('modal-open');
}

export function setupModalClose(modalId, closeBtnId) {
  document.getElementById(closeBtnId)?.addEventListener('click', () => closeModal(modalId));
  document.getElementById(modalId)?.addEventListener('click', (e) => {
    if (e.target.id === modalId) closeModal(modalId);
  });
}
