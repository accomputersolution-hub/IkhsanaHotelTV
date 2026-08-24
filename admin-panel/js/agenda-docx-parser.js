/**
 * Parse L&T LEAP-style schedule plain text (from Mammoth .docx extract).
 *
 * Supports:
 * - Times with dots: 12.30 p.m., 07.00 a.m., 09.00am, 03.15 p.m.- 04.00 p.m.
 * - Open-ended: 04.00 p.m. onwards
 * - Date headers: June 27, 2026
 * - Session "Title: Venue" after time (tabs / multi-space, no pipe required)
 * - Important Contact Details bullets
 */

/** One clock token: 12.30 p.m. | 12:30 PM | 09.00am */
const CLOCK =
  String.raw`\d{1,2}[.:]\d{2}\s*(?:a\.?m\.?|p\.?m\.?|am|pm)?`;

/**
 * Leading time range on a schedule row.
 * Examples:
 *   12.30 p.m. - 02.00 p.m. Lunch: Dining Hall B
 *   03.15 p.m.- 04.00 p.m. Inauguration: Full Auditorium
 *   04.00 p.m. onwards Get Together: Multipurpose Hall 3
 *   08.30 a.m. onwards Commencement of Session
 */
const TIME_PREFIX = new RegExp(
  String.raw`^\s*((?:${CLOCK})(?:\s*[-–—]\s*(?:${CLOCK})|\s*[-–—]?\s*onwards\.?)?)\s+(.+)$`,
  'i',
);

/** Month Day, Year — e.g. June 27, 2026 */
const MONTH_DAY_YEAR =
  /^(?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{1,2},?\s+\d{4}$/i;

const DATE_LINE =
  /(?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{1,2},?\s+\d{4}|(?:mon|tue|wed|thu|fri|sat|sun)[a-z]*\.?,?\s+\d{1,2}\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+\d{2,4}|\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}-\d{2}-\d{2}/i;

const CONTACT_HEADING = /^\s*important\s+contact\s+details\s*$/i;
const CONTACT_BULLET =
  /^\s*(?:[•\-\*]|\u2022)?\s*(Emergency\s*Number|Medical\s*Center|LDA\s*Coordinator|Contact|Coordinator|Helpdesk|Phone|Mobile|Email)\s*[-–—:]?\s*(.+)$/i;

const ISO_DATE = /\b(\d{4}-\d{2}-\d{2})\b/;

const MONTHS = {
  january: '01',
  february: '02',
  march: '03',
  april: '04',
  may: '05',
  june: '06',
  july: '07',
  august: '08',
  september: '09',
  october: '10',
  november: '11',
  december: '12',
};

/**
 * Normalize clock display: keep human-readable but tidy spaces.
 * @param {string} time
 */
function normalizeTimeLabel(time) {
  return String(time || '')
    .replace(/\s+/g, ' ')
    .replace(/\s*-\s*/g, ' - ')
    .replace(/\s*onwards\.?/gi, ' onwards')
    .trim();
}

/**
 * @param {string} label e.g. June 27, 2026
 * @returns {string} yyyy-MM-dd when parseable, else original label
 */
export function normalizeAgendaDate(label) {
  const raw = String(label || '').trim();
  const iso = raw.match(ISO_DATE);
  if (iso) return iso[1];

  const m = raw.match(
    /^(January|February|March|April|May|June|July|August|September|October|November|December)\s+(\d{1,2}),?\s+(\d{4})$/i,
  );
  if (!m) return raw;
  const mm = MONTHS[m[1].toLowerCase()];
  const dd = String(parseInt(m[2], 10)).padStart(2, '0');
  return `${m[3]}-${mm}-${dd}`;
}

/**
 * Split "Lunch: Dining Hall B" / multi-space columns into title + location.
 * @param {string} rest
 */
function splitTitleLocation(rest) {
  let text = String(rest || '').trim();
  if (!text) return { title: '', location: '' };

  // Tab or 2+ spaces often separate Time | Session in Word tables after Mammoth
  const wide = text.split(/\t+|\s{2,}/).map((p) => p.trim()).filter(Boolean);
  if (wide.length >= 2) {
    text = wide.join(': ');
  }

  const pipeParts = text.split(/\s*[|•·]\s*/).map((p) => p.trim()).filter(Boolean);
  if (pipeParts.length >= 2) {
    return { title: pipeParts[0], location: pipeParts.slice(1).join(' · ') };
  }

  // "Inauguration: Full Auditorium (Learning Centre II)"
  const colon = text.match(/^([^:]{2,80}?)\s*:\s*(.+)$/);
  if (colon) {
    return { title: colon[1].trim(), location: colon[2].trim() };
  }

  const atSplit = text.split(/\s+@\s+|\s+at\s+/i);
  if (atSplit.length === 2) {
    return { title: atSplit[0].trim(), location: atSplit[1].trim() };
  }

  return { title: text, location: '' };
}

/**
 * @param {string} raw
 * @returns {{ items: Array<{time:string,title:string,location:string,date:string,notes:string}>, contacts: string[], scheduleDate: string|null }}
 */
export function parseAgendaPlainText(raw) {
  const lines = String(raw || '')
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .split('\n')
    .map((l) => l.replace(/\u00A0/g, ' ').replace(/\t/g, '  ').trim())
    .filter(Boolean);

  let activeDate = '';
  let inContacts = false;
  const contacts = [];
  const items = [];
  /** @type {{ time: string, title: string, location: string, date: string, notes: string } | null} */
  let pending = null;

  const flushPending = () => {
    if (!pending) return;
    if (pending.time && pending.title) items.push(pending);
    pending = null;
  };

  for (const line of lines) {
    if (CONTACT_HEADING.test(line) || /^important\s+contact/i.test(line)) {
      flushPending();
      inContacts = true;
      continue;
    }

    if (inContacts || CONTACT_BULLET.test(line)) {
      flushPending();
      inContacts = true;
      const bullet = line.match(CONTACT_BULLET);
      if (bullet) {
        contacts.push(`${bullet[1].trim()}: ${bullet[2].trim()}`);
      } else if (!/^(time|session|schedule|leap)\b/i.test(line)) {
        contacts.push(line.replace(/^[\u2022•\-\*]\s*/, '').trim());
      }
      continue;
    }

    // Standalone date header line (common in L&T docs)
    if (MONTH_DAY_YEAR.test(line) || (DATE_LINE.test(line) && line.length < 40 && !TIME_PREFIX.test(line))) {
      flushPending();
      const dm = line.match(DATE_LINE);
      activeDate = normalizeAgendaDate(dm ? dm[0] : line);
      continue;
    }

    const timeMatch = line.match(TIME_PREFIX);
    if (timeMatch) {
      flushPending();
      const time = normalizeTimeLabel(timeMatch[1]);
      const { title, location } = splitTitleLocation(timeMatch[2]);
      if (!title) continue;
      // Skip non-schedule clock phrases (e.g. "09.00am to 06.00pm" medical hours)
      if (/^(to|from)\s+\d/i.test(title)) continue;
      if (!/[-–—]|onwards/i.test(time) && title.length < 3) continue;
      pending = {
        time,
        title,
        location,
        date: activeDate,
        notes: '',
      };
      continue;
    }

    // Continuation line under a time row (e.g. venue on next line)
    if (pending && !TIME_PREFIX.test(line) && !MONTH_DAY_YEAR.test(line)) {
      if (!pending.location) {
        pending.location = line;
      } else {
        pending.title = `${pending.title} ${line}`.trim();
      }
      continue;
    }
  }

  flushPending();

  const contactNotes = contacts.join(' · ');
  if (contactNotes && items.length) {
    items[0] = { ...items[0], notes: contactNotes };
  }

  const scheduleDate =
    items.find((i) => i.date)?.date ||
    (String(raw).match(ISO_DATE) || [])[1] ||
    null;

  return { items, contacts, scheduleDate };
}
