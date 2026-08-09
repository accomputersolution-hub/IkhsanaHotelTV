/**
 * Global TV ticker — Realtime Database
 * Path: hotel_settings/{hotelId}/global_announcement
 * Example: hotel_settings/lnt_academy/global_announcement
 */

import { rtdb } from './firebase-config.js';
import {
  ref as rtdbRef,
  set as rtdbSet,
  onValue,
} from 'https://www.gstatic.com/firebasejs/11.6.0/firebase-database.js';
import { toast } from './utils.js';
import { normalizeHotelId } from './firebase-config.js';
import { getHotelId, onHotelChange } from './tenant-context.js';

/** @type {(() => void) | null} */
let announcementUnsub = null;
let publishing = false;

export function announcementPath(hotelId = getHotelId()) {
  const id = normalizeHotelId(hotelId);
  if (!id) throw new Error('No active hotel context');
  return `hotel_settings/${id}/global_announcement`;
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

  onHotelChange(() => listenAnnouncement());
}

function listenAnnouncement() {
  if (typeof announcementUnsub === 'function') {
    announcementUnsub();
    announcementUnsub = null;
  }

  const input = document.getElementById('global-announcement-input');
  const hint = document.getElementById('global-announcement-path');
  const hotelId = normalizeHotelId(getHotelId());
  if (!hotelId) {
    if (input) input.value = '';
    if (hint) hint.textContent = 'Select a hotel to edit the TV ticker.';
    return;
  }

  const path = announcementPath(hotelId);
  if (hint) hint.textContent = path;

  try {
    announcementUnsub = onValue(
      rtdbRef(rtdb, path),
      (snapshot) => {
        if (!input || document.activeElement === input) return;
        const value = snapshot.exists() ? snapshot.val() : '';
        input.value = value == null ? '' : String(value);
      },
      (err) => {
        console.error('[announcement] RTDB listen failed', err);
        toast(err?.message || 'Could not load TV announcement', 'error');
      },
    );
  } catch (err) {
    console.error('[announcement] RTDB listen threw', err);
  }
}

async function publishAnnouncement() {
  const input = document.getElementById('global-announcement-input');
  const btn = document.getElementById('global-announcement-save');
  if (!input || publishing) return;

  let path;
  try {
    path = announcementPath();
  } catch (err) {
    toast(err?.message || 'No hotel selected', 'error');
    return;
  }

  const text = String(input.value ?? '').trim();
  publishing = true;
  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Publishing…';
  }

  try {
    await rtdbSet(rtdbRef(rtdb, path), text);
    toast(text ? 'TV ticker updated' : 'TV ticker cleared', 'success');
  } catch (err) {
    console.error('[announcement] RTDB write failed', err);
    toast(err?.message || 'Failed to update TV ticker', 'error');
  } finally {
    publishing = false;
    if (btn) {
      btn.disabled = false;
      btn.textContent = 'Set Ticker';
    }
  }
}
