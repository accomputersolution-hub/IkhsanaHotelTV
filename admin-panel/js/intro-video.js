/**
 * Intro / splash video for guest TVs.
 *
 * Storage:  hotels/{hotelId}/intro/intro.mp4
 * Firestore: Hotels/{hotelId}/Config/intro  → { introVideoUrl, updatedAt }
 * Also mirrors introVideoUrl on Hotels/{hotelId} for branding listeners.
 */

import { db, storage } from './firebase-config.js';
import {
  doc,
  onSnapshot,
  setDoc,
  serverTimestamp,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-firestore.js';
import {
  ref as storageRef,
  uploadBytesResumable,
  getDownloadURL,
  deleteObject,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-storage.js';
import { toast } from './utils.js';
import { logFirestoreWrite, logFirestoreListen, paths } from './paths.js';
import { getHotelId, onHotelChange } from './tenant-context.js';

const MAX_BYTES = 80 * 1024 * 1024; // 80 MB
const ACCEPT_TYPES = new Set(['video/mp4', 'video/quicktime']);

/** @type {(() => void) | null} */
let introUnsub = null;
/** @type {string} */
let currentUrl = '';
let uploading = false;

export function initIntroVideo() {
  setupUi();
  listenIntro();
  onHotelChange(() => listenIntro());
}

function setupUi() {
  const fileInput = document.getElementById('intro-video-input');
  const uploadBtn = document.getElementById('intro-video-upload-btn');
  const clearBtn = document.getElementById('intro-video-clear-btn');
  const urlInput = document.getElementById('intro-video-url-input');
  const saveUrlBtn = document.getElementById('intro-video-save-url-btn');

  uploadBtn?.addEventListener('click', () => fileInput?.click());
  fileInput?.addEventListener('change', () => {
    const file = fileInput.files?.[0];
    if (file) void uploadIntroFile(file);
    fileInput.value = '';
  });
  clearBtn?.addEventListener('click', () => void clearIntroVideo());
  saveUrlBtn?.addEventListener('click', () => {
    const url = String(urlInput?.value || '').trim();
    void saveIntroUrl(url, { fromManualUrl: true });
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
      ? `Hotels/${hotelId}/Config/intro · storage hotels/${hotelId}/intro/`
      : 'Select a hotel to manage the intro video.';
  }

  if (urlInput && document.activeElement !== urlInput) {
    urlInput.value = currentUrl;
  }

  if (clearBtn) clearBtn.disabled = !currentUrl || uploading;

  if (statusEl) {
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

function setProgress(pct, label) {
  const bar = document.getElementById('intro-video-progress');
  const fill = document.getElementById('intro-video-progress-fill');
  const text = document.getElementById('intro-video-progress-text');
  if (!bar || !fill) return;
  if (pct == null) {
    bar.classList.add('hidden');
    return;
  }
  bar.classList.remove('hidden');
  fill.style.width = `${Math.max(0, Math.min(100, pct))}%`;
  if (text) text.textContent = label || `${Math.round(pct)}%`;
}

async function uploadIntroFile(file) {
  const hotelId = getHotelId();
  if (!hotelId) {
    toast('No hotel selected', 'error');
    return;
  }
  if (uploading) return;

  const type = String(file.type || '').toLowerCase();
  const name = String(file.name || '').toLowerCase();
  if (!ACCEPT_TYPES.has(type) && !name.endsWith('.mp4')) {
    toast('Please upload an .mp4 video', 'error');
    return;
  }
  if (file.size > MAX_BYTES) {
    toast('Video must be under 80 MB', 'error');
    return;
  }

  uploading = true;
  setUploadBusy(true);
  setProgress(0, 'Starting upload…');

  const objectPath = `hotels/${hotelId}/intro/intro.mp4`;
  const ref = storageRef(storage, objectPath);

  try {
    const task = uploadBytesResumable(ref, file, {
      contentType: type || 'video/mp4',
      cacheControl: 'public,max-age=3600',
      customMetadata: {
        hotelId,
        originalName: file.name || 'intro.mp4',
      },
    });

    await new Promise((resolve, reject) => {
      task.on(
        'state_changed',
        (snap) => {
          const pct = snap.totalBytes ? (snap.bytesTransferred / snap.totalBytes) * 100 : 0;
          setProgress(pct, `Uploading… ${Math.round(pct)}%`);
        },
        reject,
        resolve,
      );
    });

    setProgress(100, 'Saving URL…');
    const downloadUrl = await getDownloadURL(task.snapshot.ref);
    await persistIntroUrl(hotelId, downloadUrl, { storagePath: objectPath });
    toast('Intro video uploaded — TVs will play it on next launch');
  } catch (err) {
    console.error('[Storage ERROR] Intro upload failed:', err);
    toast(err?.message || 'Intro video upload failed', 'error');
  } finally {
    uploading = false;
    setUploadBusy(false);
    setProgress(null);
  }
}

async function saveIntroUrl(url, { fromManualUrl = false } = {}) {
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
  uploading = true;
  setUploadBusy(true);
  try {
    await persistIntroUrl(hotelId, trimmed, { fromManualUrl });
    toast('Intro video URL saved');
  } catch (err) {
    console.error('[Firestore ERROR] Intro URL save failed:', err);
    toast(err?.message || 'Failed to save intro video URL', 'error');
  } finally {
    uploading = false;
    setUploadBusy(false);
  }
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

  // Mirror on hotel root (top-level only — do not replace nested branding map).
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
  if (uploading) return;
  uploading = true;
  setUploadBusy(true);
  try {
    try {
      await deleteObject(storageRef(storage, `hotels/${hotelId}/intro/intro.mp4`));
    } catch (err) {
      // Object may not exist when only a manual URL was set.
      console.warn('[Storage] Intro delete skipped:', err?.code || err?.message || err);
    }
    await persistIntroUrl(hotelId, '');
    toast('Intro video cleared — TVs skip to Home');
  } catch (err) {
    console.error('[Firestore ERROR] Intro clear failed:', err);
    toast(err?.message || 'Failed to clear intro video', 'error');
  } finally {
    uploading = false;
    setUploadBusy(false);
  }
}

function setUploadBusy(busy) {
  const uploadBtn = document.getElementById('intro-video-upload-btn');
  const clearBtn = document.getElementById('intro-video-clear-btn');
  const saveUrlBtn = document.getElementById('intro-video-save-url-btn');
  if (uploadBtn) {
    uploadBtn.disabled = busy;
    uploadBtn.textContent = busy ? 'Uploading…' : 'Upload .mp4';
  }
  if (saveUrlBtn) saveUrlBtn.disabled = busy;
  if (clearBtn) clearBtn.disabled = busy || !currentUrl;
}
