# Dynamic Today’s Agenda (.docx → Firebase → TV)

Corporate TVs show **Today’s Agenda** from Firestore:

`Hotels/{hotelId}/Daily_Agenda/{itemId}`

## Schema

| Field | Type | Notes |
|-------|------|--------|
| `time` | string | e.g. `09:00 AM - 10:30 AM` |
| `title` | string | Session name |
| `location` / `venue` | string | Room / hall |
| `date` | string | Optional `yyyy-MM-dd` or label; blank = always on today’s board |
| `notes` | string | Contacts / footnotes |
| `sortOrder` | number | Import order |
| `source` | string | `docx_import` or manual |

## Admin workflow

1. Open corporate property PMS → **Daily Agenda**
2. **Import .docx** (L&T-style Word schedule) **or** add rows manually
3. Import **replaces** the current collection; TVs update via `onSnapshot`

### .docx line formats that parse well

```
Monday, 24 Aug 2026
09:00 AM - 10:30 AM | Leadership Kickoff | Conference Hall A
10:45 AM - 12:00 PM Strategy Workshop @ Hall B
Contact: Helpdesk Ext 2200
```

Browser uses [Mammoth](https://github.com/mwilliamson/mammoth.js) for text extract; parsing matches Android `AgendaScheduleParser`.

## Android

- `agenda/AgendaScheduleParser.kt` — ZIP/OOXML or plain-text parse (no Apache POI)
- `agenda/AgendaFirestoreSync.kt` — optional Kotlin push to Firestore
- `ui/agenda/AgendaScreen.kt` — live Compose timeline (`TodayAgendaSessionCard`)
- Filters to **today** when `date` is set; blank dates show as the current board
