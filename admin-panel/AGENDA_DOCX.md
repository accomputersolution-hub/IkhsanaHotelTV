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

### .docx line formats that parse well (L&T LEAP)

```
June 27, 2026
12.30 p.m. - 02.00 p.m. Lunch: Dining Hall B
03.15 p.m.- 04.00 p.m. Inauguration: Full Auditorium (Learning Centre II)
04.00 p.m. onwards Hi Tea: Near Auditorium
08.30 a.m. onwards Commencement of Session
Learning Centre II

Important Contact Details
• Emergency Number- 900
• LDA Coordinator: Ms. Pooja Shetty (02114-302212)
```

Supports dots in times (`12.30`), `a.m.` / `p.m.`, tabs/multi-space between columns, and `Title: Venue`.

## Android

- `agenda/AgendaScheduleParser.kt` — ZIP/OOXML or plain-text parse (no Apache POI)
- `agenda/AgendaFirestoreSync.kt` — optional Kotlin push to Firestore
- `ui/agenda/AgendaScreen.kt` — live Compose timeline (`TodayAgendaSessionCard`)
- Filters to **today** when `date` is set; blank dates show as the current board
