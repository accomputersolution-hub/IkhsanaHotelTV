/**
 * Intro / splash video for guest TVs.
 *
 * Storage:  hotels/{hotelId}/intro/intro.mp4
 * Firestore: Hotels/{hotelId}/Config/intro  → { introVideoUrl, updatedAt }
 * Also mirrors introVideoUrl on Hotels/{hotelId} for branding listeners.
 */

import { auth, db, storage } from './firebase-config.js';
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
/** If bytesTransferred stays unchanged this long, cancel and surface an error. */
const STALL_MS = 12_000;

/** @type {(() => void) | null} */
let introUnsub = null;
/** @type {string} */
let currentUrl = '';
let uploading = false;
/** @type {import('firebase/storage').UploadTask | null} */
let activeUploadTask = null;

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

  if (statusEl && !uploading) {
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

function setProgress(pct, label, { error = false } = {}) {
  const bar = document.getElementById('intro-video-progress');
  const fill = document.getElementById('intro-video-progress-fill');
  const text = document.getElementById('intro-video-progress-text');
  if (!bar || !fill) return;
  if (pct == null && !error) {
    bar.classList.add('hidden');
    bar.classList.remove('is-error');
    return;
  }
  bar.classList.remove('hidden');
  bar.classList.toggle('is-error', Boolean(error));
  if (pct != null) {
    fill.style.width = `${Math.max(0, Math.min(100, pct))}%`;
  }
  if (text) text.textContent = label || (pct != null ? `${Math.round(pct)}%` : '');
}

function formatBytes(n) {
  if (!Number.isFinite(n) || n < 0) return '0 B';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Map Firebase Storage errors to actionable admin copy.
 * Stuck-at-0% is almost always storage/unauthorized (rules) or CORS/network.
 */
function formatStorageError(err) {
  const code = String(err?.code || '');
  const raw = String(err?.message || err || 'Intro video upload failed');

  if (code === 'storage/unauthenticated' || /not authenticated|auth/i.test(raw)) {
    return 'Not signed in to Firebase Auth. Sign out and sign in again, then retry the upload.';
  }
  if (code === 'storage/unauthorized' || code === 'storage/permission-denied') {
    return (
      'Firebase Storage rules blocked this upload. In Firebase Console → Storage → Rules, ' +
      'allow authenticated writes to hotels/{hotelId}/intro/** (see admin-panel/storage.rules.example).'
    );
  }
  if (code === 'storage/canceled') {
    return 'Upload canceled.';
  }
  if (code === 'storage/stall' || /stall/i.test(raw)) {
    return (
      'Upload stuck at 0% (no bytes transferred). Usually Storage security rules or CORS. ' +
      'Check Console → Storage → Rules, or paste a public HTTPS .mp4 URL below as a workaround.'
    );
  }
  if (code === 'storage/retry-limit-exceeded' || /cors|network|failed to fetch/i.test(raw)) {
    return (
      `Network/CORS error talking to Firebase Storage: ${raw}. ` +
      'Confirm storageBucket in firebase-config.js and that Storage is enabled for this project.'
    );
  }
  if (code === 'storage/quota-exceeded') {
    return 'Firebase Storage quota exceeded for this project.';
  }
  if (code === 'storage/invalid-checksum' || code === 'storage/server-file-wrong-size') {
    return 'Upload corrupted in transit — retry the same .mp4 file.';
  }
  return code ? `${code}: ${raw}` : raw;
}

async function uploadIntroFile(file) {
  const hotelId = getHotelId();
  if (!hotelId) {
    toast('No hotel selected', 'error');
    return;
  }
  if (uploading) return;

  if (!auth.currentUser) {
    const msg = formatStorageError({ code: 'storage/unauthenticated' });
    console.error('[Storage ERROR] Intro upload blocked — no auth.currentUser');
    toast(msg, 'error');
    setProgress(0, msg, { error: true });
    return;
  }

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
  setProgress(0, `Starting… 0 / ${formatBytes(file.size)}`);

  const objectPath = `hotels/${hotelId}/intro/intro.mp4`;
  const ref = storageRef(storage, objectPath);

  console.info('[Storage] Intro upload start', {
    hotelId,
    objectPath,
    size: file.size,
    type: type || 'video/mp4',
    uid: auth.currentUser.uid,
    bucket: storage.app?.options?.storageBucket,
  });

  try {
    const contentType = type && type.startsWith('video/') ? type : 'video/mp4';
    const task = uploadBytesResumable(ref, file, {
      contentType,
      cacheControl: 'public,max-age=3600',
      customMetadata: {
        hotelId,
        originalName: file.name || 'intro.mp4',
        uploadedBy: auth.currentUser.uid,
      },
    });
    activeUploadTask = task;

    await waitForUploadTask(task, file.size);

    setProgress(100, 'Saving download URL…');
    let downloadUrl;
    try {
      downloadUrl = await getDownloadURL(task.snapshot.ref);
    } catch (urlErr) {
      console.error('[Storage ERROR] getDownloadURL failed:', urlErr);
      throw urlErr;
    }

    try {
      await persistIntroUrl(hotelId, downloadUrl, {
        storagePath: objectPath,
        contentType,
        byteSize: file.size,
      });
    } catch (fsErr) {
      console.error('[Firestore ERROR] Saving introVideoUrl failed after upload:', fsErr);
      toast(
        `Video uploaded, but Firestore save failed: ${fsErr?.message || fsErr}. ` +
          'Paste the Storage download URL manually if needed.',
        'error',
      );
      throw fsErr;
    }

    setProgress(100, 'Done');
    toast('Intro video uploaded — TVs will play it on next launch');
    window.setTimeout(() => setProgress(null), 1200);
  } catch (err) {
    console.error('[Storage ERROR] Intro upload failed:', err);
    console.error('[Storage ERROR] code=', err?.code, 'serverResponse=', err?.serverResponse || err?.customData);
    const message = formatStorageError(err);
    toast(message, 'error');
    setProgress(0, message, { error: true });
  } finally {
    activeUploadTask = null;
    uploading = false;
    setUploadBusy(false);
  }
}

/**
 * Tracks uploadBytesResumable progress and rejects on error / stall (stuck at 0%).
 * @param {import('firebase/storage').UploadTask} task
 * @param {number} totalHint
 */
function waitForUploadTask(task, totalHint) {
  return new Promise((resolve, reject) => {
    let settled = false;
    let lastBytes = -1;
    let lastChangeAt = Date.now();

    const finish = (fn, arg) => {
      if (settled) return;
      settled = true;
      window.clearInterval(stallTimer);
      fn(arg);
    };

    const stallTimer = window.setInterval(() => {
      if (settled) return;
      const idle = Date.now() - lastChangeAt;
      if (idle < STALL_MS) return;
      console.error(
        '[Storage ERROR] Intro upload stalled',
        { lastBytes, idleMs: idle, state: task.snapshot?.state },
      );
      try {
        task.cancel();
      } catch (cancelErr) {
        console.warn('[Storage] cancel after stall failed', cancelErr);
      }
      const stallErr = new Error(
        'Upload stalled — no bytes transferred (Storage rules or CORS).',
      );
      stallErr.code = 'storage/stall';
      finish(reject, stallErr);
    }, 1500);

    task.on(
      'state_changed',
      (snapshot) => {
        const transferred = Number(snapshot.bytesTransferred) || 0;
        const total = Number(snapshot.totalBytes) || totalHint || 0;
        if (transferred !== lastBytes) {
          lastBytes = transferred;
          lastChangeAt = Date.now();
        }
        const pct = total > 0 ? (transferred / total) * 100 : 0;
        setProgress(
          pct,
          `Uploading… ${Math.round(pct)}% (${formatBytes(transferred)} / ${formatBytes(total)})`,
        );
        console.debug('[Storage] progress', {
          state: snapshot.state,
          transferred,
          total,
          pct: Math.round(pct),
        });
      },
      (err) => {
        console.error('[Storage ERROR] state_changed error callback:', err);
        finish(reject, err);
      },
      () => {
        console.info('[Storage] Intro upload complete snapshot', {
          bytes: task.snapshot?.totalBytes,
          state: task.snapshot?.state,
        });
        finish(resolve);
      },
    );
  });
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
    await persistIntroUrl(hotelId, trimmed, { fromManualUrl: Boolean(fromManualUrl) });
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
    if (activeUploadTask) {
      try {
        activeUploadTask.cancel();
      } catch (_) {
        /* ignore */
      }
      activeUploadTask = null;
    }
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
