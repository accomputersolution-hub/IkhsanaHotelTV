package `in`.pcncloud.hotel.agenda

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Extracts structured schedule rows from Word (.docx) or plain-text agendas
 * (L&T LEAP format: `12.30 p.m. - 02.00 p.m. Lunch: Dining Hall B`).
 *
 * .docx is ZIP + `word/document.xml` (no Apache POI).
 */
object AgendaScheduleParser {

    data class ParsedAgenda(
        val items: List<ParsedSession> = emptyList(),
        val contacts: List<String> = emptyList(),
        val scheduleDate: String? = null,
    )

    data class ParsedSession(
        val time: String,
        val title: String,
        val location: String = "",
        val date: String = "",
        val notes: String = "",
    )

    private const val CLOCK =
        """\d{1,2}[.:]\d{2}\s*(?:a\.?m\.?|p\.?m\.?|am|pm)?"""

    private val TIME_PREFIX = Regex(
        """(?i)^\s*((?:$CLOCK)(?:\s*[-–—]\s*(?:$CLOCK)|\s*[-–—]?\s*onwards\.?)?)\s+(.+)$""",
    )

    private val MONTH_DAY_YEAR = Regex(
        """(?i)^(January|February|March|April|May|June|July|August|September|October|November|December)\s+(\d{1,2}),?\s+(\d{4})$""",
    )

    private val DATE_LINE = Regex(
        """(?i)(?:January|February|March|April|May|June|July|August|September|October|November|December)\s+\d{1,2},?\s+\d{4}""" +
            """|(?:mon|tue|wed|thu|fri|sat|sun)[a-z]*\.?,?\s+\d{1,2}\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+\d{2,4}""" +
            """|\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{4}-\d{2}-\d{2}""",
    )

    private val CONTACT_HEADING = Regex("""(?i)^\s*important\s+contact\s+details\s*$""")
    private val CONTACT_BULLET = Regex(
        """(?i)^\s*(?:[•\-*]|\u2022)?\s*(Emergency\s*Number|Medical\s*Center|LDA\s*Coordinator|Contact|Coordinator|Helpdesk|Phone|Mobile|Email)\s*[-–—:]?\s*(.+)$""",
    )
    private val ISO_DATE = Regex("""\b(\d{4}-\d{2}-\d{2})\b""")

    private val MONTHS = mapOf(
        "january" to "01", "february" to "02", "march" to "03", "april" to "04",
        "may" to "05", "june" to "06", "july" to "07", "august" to "08",
        "september" to "09", "october" to "10", "november" to "11", "december" to "12",
    )

    fun parseDocx(input: InputStream): ParsedAgenda {
        val text = extractTextFromDocx(input)
        return parsePlainText(text)
    }

    fun parsePlainText(raw: String): ParsedAgenda {
        val lines = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { it.replace('\u00A0', ' ').replace('\t', ' ').trim() }
            .filter { it.isNotEmpty() }

        var activeDate = ""
        var inContacts = false
        val contacts = mutableListOf<String>()
        val items = mutableListOf<ParsedSession>()
        var pending: ParsedSession? = null

        fun flushPending() {
            val p = pending ?: return
            if (p.time.isNotBlank() && p.title.isNotBlank()) items += p
            pending = null
        }

        for (line in lines) {
            if (CONTACT_HEADING.containsMatchIn(line) || line.contains("Important Contact", ignoreCase = true)) {
                flushPending()
                inContacts = true
                continue
            }

            val contactBullet = CONTACT_BULLET.find(line)
            if (inContacts || contactBullet != null) {
                flushPending()
                inContacts = true
                if (contactBullet != null) {
                    contacts += "${contactBullet.groupValues[1].trim()}: ${contactBullet.groupValues[2].trim()}"
                } else if (!line.matches(Regex("""(?i)^(time|session|schedule|leap)\b.*"""))) {
                    contacts += line.replace(Regex("""^[\u2022•\-*]\s*"""), "").trim()
                }
                continue
            }

            val monthDay = MONTH_DAY_YEAR.find(line)
            if (monthDay != null || (DATE_LINE.containsMatchIn(line) && line.length < 40 && !TIME_PREFIX.containsMatchIn(line))) {
                flushPending()
                activeDate = normalizeDateLabel(monthDay?.value ?: DATE_LINE.find(line)!!.value)
                continue
            }

            val timeMatch = TIME_PREFIX.find(line)
            if (timeMatch != null) {
                flushPending()
                val time = normalizeTimeLabel(timeMatch.groupValues[1])
                val (title, location) = splitTitleLocation(timeMatch.groupValues[2])
                if (title.isBlank()) continue
                if (title.matches(Regex("""(?i)^(to|from)\s+\d.*"""))) continue
                pending = ParsedSession(
                    time = time,
                    title = title,
                    location = location,
                    date = activeDate,
                    notes = "",
                )
                continue
            }

            val p = pending
            if (p != null && !TIME_PREFIX.containsMatchIn(line) && !MONTH_DAY_YEAR.containsMatchIn(line)) {
                pending = if (p.location.isBlank()) {
                    p.copy(location = line)
                } else {
                    p.copy(title = "${p.title} $line".trim())
                }
            }
        }

        flushPending()

        val contactNotes = contacts.joinToString(" · ")
        val withNotes = if (contactNotes.isBlank()) {
            items
        } else {
            items.mapIndexed { index, session ->
                if (index == 0) session.copy(notes = contactNotes) else session
            }
        }

        val scheduleDate = withNotes.firstOrNull { it.date.isNotBlank() }?.date
            ?: ISO_DATE.find(raw)?.groupValues?.get(1)

        return ParsedAgenda(
            items = withNotes,
            contacts = contacts,
            scheduleDate = scheduleDate,
        )
    }

    fun extractTextFromDocx(input: InputStream): String {
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val bytes = zip.readBytesCompat()
                    return xmlToPlainText(bytes.inputStream())
                }
                entry = zip.nextEntry
            }
        }
        return ""
    }

    private fun splitTitleLocation(rest: String): Pair<String, String> {
        var text = rest.trim()
        if (text.isEmpty()) return "" to ""

        val wide = text.split(Regex("""\t+|\s{2,}""")).map { it.trim() }.filter { it.isNotEmpty() }
        if (wide.size >= 2) text = wide.joinToString(": ")

        val pipeParts = text.split(Regex("""\s*[|•·]\s*""")).map { it.trim() }.filter { it.isNotEmpty() }
        if (pipeParts.size >= 2) {
            return pipeParts[0] to pipeParts.drop(1).joinToString(" · ")
        }

        val colon = Regex("""^([^:]{2,80}?)\s*:\s*(.+)$""").find(text)
        if (colon != null) {
            return colon.groupValues[1].trim() to colon.groupValues[2].trim()
        }

        val atSplit = text.split(Regex("""\s+@\s+|\s+at\s+""", RegexOption.IGNORE_CASE), limit = 2)
        if (atSplit.size == 2) {
            return atSplit[0].trim() to atSplit[1].trim()
        }

        return text to ""
    }

    private fun normalizeTimeLabel(time: String): String =
        time
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s*-\s*"""), " - ")
            .replace(Regex("""(?i)\s*onwards\.?"""), " onwards")
            .trim()

    private fun normalizeDateLabel(raw: String): String {
        val iso = ISO_DATE.find(raw)?.groupValues?.get(1)
        if (iso != null) return iso
        val m = MONTH_DAY_YEAR.find(raw.trim()) ?: return raw.trim()
        val mm = MONTHS[m.groupValues[1].lowercase()] ?: return raw.trim()
        val dd = m.groupValues[2].toIntOrNull()?.toString()?.padStart(2, '0') ?: return raw.trim()
        return "${m.groupValues[3]}-$mm-$dd"
    }

    private fun InputStream.readBytesCompat(): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (true) {
            val n = read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun xmlToPlainText(xml: InputStream): String {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser().apply { setInput(xml, "UTF-8") }
        val sb = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "p", "br", "tab" -> sb.append('\n')
                    }
                }
                XmlPullParser.TEXT -> sb.append(parser.text)
            }
            event = parser.next()
        }
        return sb.toString()
    }
}
