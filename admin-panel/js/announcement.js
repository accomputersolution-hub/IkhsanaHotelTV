/**
 * Global TV ticker — loaded/saved via Vercel Admin API (Firebase Admin SDK).
 *
 * RTDB node (TV still listens here):
 *   hotels/{hotelId}/config/global_announcement
 *
 * Client RTDB rules deny admin reads/writes on this node, so the panel must
 * go through /api/announcement instead of onValue/set from the browser.
 */

import { toast } from './utils.js';
import { normalizeHotelId } from './firebase-config.js';
import { getHotelId, onHotelChange } from './tenant-context.js';
import {
  fetchAnnouncement,
  publishAnnouncementApi,
} from './api-client.js';

let publishing = false;
let loadSeq = 0;

export function announcementPath(hotelId = getHotelId()) {
  const id = normalizeHotelId(hotelId);
  if (!id) throw new Error('No active hotel context');
  return `hotels/${id}/config/global_announcement`;
}

export function initAnnouncement() {
  const input = document.getElementById('global-announcement-input');
  const btn = document.getElementById('global-announcement-save');
  const clearBtn = document.getElementById('global-announcement-clear');
  if (!input || !btn) return;

  btn.addEventListener('click', () => publishAnnouncement());
  clearBtn?.addEventListener('click', () => {
    input.value = '';
    publishAnnouncement();
  });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      publishAnnouncement();
    }
  });

  onHotelChange(() => loadAnnouncement());
}

async function loadAnnouncement() {
  const input = document.getElementById('global-announcement-input');
  const hint = document.getElementById('global-announcement-path');
  const hotelId = normalizeHotelId(getHotelId());
  const seq = ++loadSeq;

  if (!hotelId) {
    if (input) input.value = '';
    if (hint) hint.textContent = 'Select a hotel to edit the TV ticker.';
    return;
  }

  const path = announcementPath(hotelId);
  if (hint) hint.textContent = path;

  try {
    const data = await fetchAnnouncement(hotelId);
    if (seq !== loadSeq) return;
    if (input && document.activeElement !== input) {
      input.value = data.text == null ? '' : String(data.text);
    }
  } catch (err) {
    if (seq !== loadSeq) return;
    console.error('[announcement] Admin API load failed', err);
    if (hint) {
      hint.textContent = `${path} — could not load via Admin API`;
    }
    // Do not toast on Room Status auto-load; user sees empty field until retry.
  }
}

async function publishAnnouncement() {
  const input = document.getElementById('global-announcement-input');
  const btn = document.getElementById('global-announcement-save');
  if (!input || publishing) return;

  const hotelId = normalizeHotelId(getHotelId());
  if (!hotelId) {
    toast('No hotel selected', 'error');
    return;
  }

  const text = String(input.value ?? '').trim();
  publishing = true;
  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Publishing…';
  }

  try {
    await publishAnnouncementApi(hotelId, text);
    toast(text ? 'TV ticker updated' : 'TV ticker cleared', 'success');
  } catch (err) {
    console.error('[announcement] Admin API publish failed', err);
    toast(err?.message || 'Failed to update TV ticker', 'error');
  } finally {
    publishing = false;
    if (btn) {
      btn.disabled = false;
      btn.textContent = 'Set Ticker';
    }
  }
}
