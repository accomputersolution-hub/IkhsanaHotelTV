package `in`.pcncloud.hotel.data

/**
 * Room identifiers are always [String] — numeric ("101") or named ("Middle East").
 * Display: digits-only → "Room 101"; any letters → exact name, no "Room" prefix.
 */
object RoomIds {
    fun normalize(raw: String?): String = raw?.trim().orEmpty()

    /** True when the id is non-empty and every character is a digit (0-9). */
    fun isNumericId(roomNumber: String): Boolean {
        val trimmed = roomNumber.trim()
        return trimmed.isNotEmpty() && trimmed.all { it.isDigit() }
    }

    /**
     * Guest-facing label for chrome / headers.
     * @param numericPrefix usually "Room" (hotel) — only applied for digit-only ids.
     */
    fun formatDisplay(roomNumber: String, numericPrefix: String = "Room"): String {
        val trimmed = roomNumber.trim()
        if (trimmed.isEmpty()) return trimmed
        return if (isNumericId(trimmed)) "$numericPrefix $trimmed" else trimmed
    }

    /**
     * Coerce a Firestore / RTDB field to a room id [String].
     * Numbers become integer-looking strings when whole (101.0 → "101").
     */
    fun coerceFromFirestore(value: Any?): String = when (value) {
        null -> ""
        is String -> value.trim()
        is Long, is Int, is Short, is Byte -> value.toString()
        is Double -> {
            val longVal = value.toLong()
            if (value == longVal.toDouble()) longVal.toString() else value.toString()
        }
        is Float -> {
            val longVal = value.toLong()
            if (value == longVal.toFloat()) longVal.toString() else value.toString()
        }
        is Number -> value.toString().trim()
        is Boolean -> value.toString()
        else -> value.toString().trim()
    }
}
