package `in`.pcncloud.hotel.agenda

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Extracts structured schedule rows from Word (.docx) or plain-text agendas
 * (e.g. L&T leadership program schedules).
 *
 * .docx is read as a ZIP + `word/document.xml` (no Apache POI — keeps the TV APK small).
 * Admin can also push the same schema from a browser-side Mammoth extract.
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

    private val TIME_PREFIX = Regex(
        """^\s*((?:\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)?)(?:\s*[-–—to]+\s*(?:\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)?))?)\s*(.*)$""",
    )
    private val DATE_LINE = Regex(
        """(?i)\b(?:mon|tue|wed|thu|fri|sat|sun)[a-z]*\.?,?\s+\d{1,2}\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s+\d{2,4}\b""" +
            """|\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b""" +
            """|\b\d{4}-\d{2}-\d{2}\b""",
    )
    private val CONTACT_LINE = Regex(
        """(?i)^\s*(?:contact|coordinator|helpdesk|phone|mobile|ext(?:ension)?|email)\s*[:\-]\s*(.+)$""",
    )
    private val ISO_DATE = Regex("""\b(\d{4}-\d{2}-\d{2})\b""")

    fun parseDocx(input: InputStream): ParsedAgenda {
        val text = extractTextFromDocx(input)
        return parsePlainText(text)
    }

    fun parsePlainText(raw: String): ParsedAgenda {
        val lines = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { it.replace('\u00A0', ' ').trim() }
            .filter { it.isNotEmpty() }

        var activeDate = ""
        val contacts = mutableListOf<String>()
        val items = mutableListOf<ParsedSession>()

        for (line in lines) {
            val contactMatch = CONTACT_LINE.find(line)
            if (contactMatch != null) {
                contacts += contactMatch.groupValues[1].trim()
                continue
            }

            val dateMatch = DATE_LINE.find(line)
            if (dateMatch != null) {
                activeDate = normalizeDateLabel(dateMatch.value)
                continue
            }

            val timeMatch = TIME_PREFIX.find(line) ?: continue
            val time = timeMatch.groupValues[1].replace(Regex("""\s+"""), " ").trim()
            val rest = timeMatch.groupValues[2].trim()
                .trimStart('|', '-', '–', '—', ':', '\t')
                .trim()
            if (rest.isBlank()) continue

            var location = ""
            var title = rest
            val parts = rest.split(Regex("""\s*[|•·]\s*""")).map { it.trim() }.filter { it.isNotEmpty() }
            when {
                parts.size >= 3 -> {
                    title = parts[0]
                    location = parts.drop(1).joinToString(" · ")
                }
                parts.size == 2 -> {
                    title = parts[0]
                    location = parts[1]
                }
                else -> {
                    val atSplit = rest.split(
                        Regex("""\s+@\s+|\s+at\s+""", RegexOption.IGNORE_CASE),
                        limit = 2,
                    )
                    if (atSplit.size == 2) {
                        title = atSplit[0].trim()
                        location = atSplit[1].trim()
                    }
                }
            }

            items += ParsedSession(
                time = time,
                title = title,
                location = location,
                date = activeDate,
                notes = "",
            )
        }

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

    /** Lightweight .docx → plain text via ZIP + XML (OOXML), without Apache POI. */
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

    private fun normalizeDateLabel(raw: String): String {
        val iso = ISO_DATE.find(raw)?.groupValues?.get(1)
        if (iso != null) return iso
        return raw.trim()
    }
}
