/**
 * Parse L&T-style schedule plain text (from Mammoth .docx extract) into agenda rows.
 * Mirrors Android AgendaScheduleParser heuristics.
 */

const TIME_PREFIX =
  /^\s*((?:\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)?)(?:\s*[-–—to]+\s*(?:\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)?))?)\s*(.*)$/;
const DATE_LINE =
  /(?:mon|tue|wed|thu|fri|sat|sun)[a-z]*\.?,?\s+\d{1,2}\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+\d{2,4}|\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}-\d{2}-\d{2}/i;
const CONTACT_LINE =
  /^\s*(?:contact|coordinator|helpdesk|phone|mobile|ext(?:ension)?|email)\s*[:\-]\s*(.+)$/i;
const ISO_DATE = /\b(\d{4}-\d{2}-\d{2})\b/;

/**
 * @param {string} raw
 * @returns {{ items: Array<{time:string,title:string,location:string,date:string,notes:string}>, contacts: string[], scheduleDate: string|null }}
 */
export function parseAgendaPlainText(raw) {
  const lines = String(raw || '')
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .split('\n')
    .map((l) => l.replace(/\u00A0/g, ' ').trim())
    .filter(Boolean);

  let activeDate = '';
  const contacts = [];
  const items = [];

  for (const line of lines) {
    const contactMatch = line.match(CONTACT_LINE);
    if (contactMatch) {
      contacts.push(contactMatch[1].trim());
      continue;
    }

    const dateMatch = line.match(DATE_LINE);
    if (dateMatch) {
      activeDate = dateMatch[0].trim();
      continue;
    }

    const timeMatch = line.match(TIME_PREFIX);
    if (!timeMatch) continue;

    const time = timeMatch[1].replace(/\s+/g, ' ').trim();
    let rest = timeMatch[2].trim().replace(/^[\|\-–—:\t]+/, '').trim();
    if (!rest) continue;

    let title = rest;
    let location = '';
    const parts = rest.split(/\s*[|•·]\s*/).map((p) => p.trim()).filter(Boolean);
    if (parts.length >= 3) {
      title = parts[0];
      location = parts.slice(1).join(' · ');
    } else if (parts.length === 2) {
      title = parts[0];
      location = parts[1];
    } else {
      const atSplit = rest.split(/\s+@\s+|\s+at\s+/i);
      if (atSplit.length === 2) {
        title = atSplit[0].trim();
        location = atSplit[1].trim();
      }
    }

    items.push({
      time,
      title,
      location,
      date: activeDate,
      notes: '',
    });
  }

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
