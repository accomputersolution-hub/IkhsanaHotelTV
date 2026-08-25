/**
 * Intro / splash video for guest TVs (URL-only — no Storage upload).
 *
 * Firestore: Hotels/{hotelId}/Config/intro  → { introVideoUrl, updatedAt }
 * Also mirrors introVideoUrl on Hotels/{hotelId} for branding listeners.
 */

import { db } from './firebase-config.js';
import {
  doc,
  onSnapshot,
  setDoc,
  serverTimestamp,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import { toast } from './utils.js';
import { logFirestoreWrite, logFirestoreListen, paths } from './paths.js';
import { getHotelId, onHotelChange } from './tenant-context.js';

/** @type {(() => void) | null} */
let introUnsub = null;
/** @type {string} */
let currentUrl = '';
let busy = false;

export function initIntroVideo() {
  setupUi();
  listenIntro();
  onHotelChange(() => listenIntro());
}

function setupUi() {
  const clearBtn = document.getElementById('intro-video-clear-btn');
  const urlInput = document.getElementById('intro-video-url-input');
  const saveUrlBtn = document.getElementById('intro-video-save-url-btn');

  clearBtn?.addEventListener('click', () => void clearIntroVideo());
  saveUrlBtn?.addEventListener('click', () => {
    const url = String(urlInput?.value || '').trim();
    void saveIntroUrl(url);
  });
}

function introDocRef(hotelId) {
  return doc(db, 'Hotels', hotelId, 'Config', 'intro');
}

function listenIntro() {
  if (introUnsub) {
    introUnsub();
    introUnsub = null;
  }
  currentUrl = '';
  renderStatus();

  const hotelId = getHotelId();
  if (!hotelId) return;

  const path = paths.introConfigDoc();
  logFirestoreListen('Intro Video Config', path);
  introUnsub = onSnapshot(
    introDocRef(hotelId),
    (snap) => {
      const data = snap.exists() ? snap.data() : {};
      currentUrl = String(data?.introVideoUrl || data?.intro_video_url || '').trim();
      renderStatus();
    },
    (err) => {
      console.error('[Firestore ERROR] Intro video listen:', err);
      toast('Failed to load intro video config', 'error');
    },
  );
}

function renderStatus() {
  const pathEl = document.getElementById('intro-video-path');
  const statusEl = document.getElementById('intro-video-status');
  const preview = document.getElementById('intro-video-preview');
  const urlInput = document.getElementById('intro-video-url-input');
  const clearBtn = document.getElementById('intro-video-clear-btn');
  const hotelId = getHotelId();

  if (pathEl) {
    pathEl.textContent = hotelId
      ? `Hotels/${hotelId}/Config/intro`
      : 'Select a hotel to manage the intro video.';
  }

  if (urlInput && document.activeElement !== urlInput) {
    urlInput.value = currentUrl;
  }

  if (clearBtn) clearBtn.disabled = !currentUrl || busy;

  if (statusEl && !busy) {
    statusEl.textContent = currentUrl
      ? 'Intro video is active — TVs play this on launch, then open Home.'
      : 'No intro video — TVs skip straight to Home.';
  }

  if (preview) {
    if (currentUrl) {
      preview.classList.remove('hidden');
      if (preview.getAttribute('src') !== currentUrl) {
        preview.src = currentUrl;
      }
    } else {
      preview.removeAttribute('src');
      preview.classList.add('hidden');
    }
  }
}

async function saveIntroUrl(url) {
  const hotelId = getHotelId();
  if (!hotelId) {
    toast('No hotel selected', 'error');
    return;
  }
  const trimmed = String(url || '').trim();
  if (!trimmed) {
    await clearIntroVideo();
    return;
  }
  if (!/^https?:\/\//i.test(trimmed)) {
    toast('Enter a valid https:// video URL', 'error');
    return;
  }
  busy = true;
  setBusy(true);
  try {
    const sizeHint = await probeVideoUrlBytes(trimmed);
    if (sizeHint === 0) {
      toast(
        'Warning: that URL reports 0 bytes (empty file). TV will skip playback. Use a real .mp4 host link.',
        'error',
      );
    } else if (sizeHint != null && sizeHint > 0) {
      console.info('[Intro] URL Content-Length=', sizeHint);
    }
    await persistIntroUrl(hotelId, trimmed, {
      fromManualUrl: true,
      probedBytes: sizeHint,
    });
    toast(
      sizeHint === 0
        ? 'URL saved, but file looks empty — replace with a valid .mp4'
        : 'Intro video URL saved',
    );
  } catch (err) {
    console.error('[Firestore ERROR] Intro URL save failed:', err);
    toast(err?.message || 'Failed to save intro video URL', 'error');
  } finally {
    busy = false;
    setBusy(false);
  }
}

/** Best-effort HEAD/Range probe — catches 0-byte CDN links before TV fails. */
async function probeVideoUrlBytes(url) {
  try {
    const head = await fetch(url, { method: 'HEAD', mode: 'cors' });
    const len = head.headers.get('content-length');
    if (len != null && len !== '') {
      const n = Number(len);
      if (Number.isFinite(n)) return n;
    }
  } catch (err) {
    console.warn('[Intro] HEAD probe failed (CORS?)', err?.message || err);
  }
  try {
    const range = await fetch(url, {
      method: 'GET',
      headers: { Range: 'bytes=0-0' },
      mode: 'cors',
    });
    const cr = range.headers.get('content-range');
    const m = cr && /\/(\d+)\s*$/.exec(cr);
    if (m) return Number(m[1]);
    const len = range.headers.get('content-length');
    if (len != null) return Number(len);
  } catch (err) {
    console.warn('[Intro] Range probe failed', err?.message || err);
  }
  return null;
}

async function persistIntroUrl(hotelId, introVideoUrl, meta = {}) {
  const payload = {
    introVideoUrl,
    intro_video_url: introVideoUrl,
    updatedAt: serverTimestamp(),
    ...meta,
  };
  await setDoc(introDocRef(hotelId), payload, { merge: true });
  logFirestoreWrite('Intro Video Config', paths.introConfigDoc(), payload);

  await setDoc(
    doc(db, 'Hotels', hotelId),
    {
      introVideoUrl,
      intro_video_url: introVideoUrl,
      updatedAt: serverTimestamp(),
    },
    { merge: true },
  );
  logFirestoreWrite('Intro Video Hotel Mirror', `Hotels/${hotelId}`, {
    introVideoUrl,
  });
  currentUrl = introVideoUrl;
  renderStatus();
}

async function clearIntroVideo() {
  const hotelId = getHotelId();
  if (!hotelId) {
    toast('No hotel selected', 'error');
    return;
  }
  if (busy) return;
  busy = true;
  setBusy(true);
  try {
    await persistIntroUrl(hotelId, '');
    toast('Intro video cleared — TVs skip to Home');
  } catch (err) {
    console.error('[Firestore ERROR] Intro clear failed:', err);
    toast(err?.message || 'Failed to clear intro video', 'error');
  } finally {
    busy = false;
    setBusy(false);
  }
}

function setBusy(isBusy) {
  const clearBtn = document.getElementById('intro-video-clear-btn');
  const saveUrlBtn = document.getElementById('intro-video-save-url-btn');
  if (saveUrlBtn) saveUrlBtn.disabled = isBusy;
  if (clearBtn) clearBtn.disabled = isBusy || !currentUrl;
}
