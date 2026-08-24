package `in`.pcncloud.hotel.agenda

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import `in`.pcncloud.hotel.data.FirestorePaths
import kotlinx.coroutines.tasks.await

/**
 * Writes parsed agenda sessions to `Hotels/{hotelId}/Daily_Agenda/{id}`.
 * Prefer admin-panel import for ops; this is available for TV tooling / tests.
 */
object AgendaFirestoreSync {

    /**
     * @param replaceIfTrue clears existing Daily_Agenda docs first (full schedule replace).
     */
    suspend fun syncParsedAgenda(
        hotelId: String,
        parsed: AgendaScheduleParser.ParsedAgenda,
        replace: Boolean = true,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    ): Int {
        val normalizedHotel = FirestorePaths.normalizeHotelId(hotelId)
        require(normalizedHotel.isNotBlank()) { "hotelId required" }
        require(parsed.items.isNotEmpty()) { "No agenda sessions to sync" }

        val col = firestore
            .collection(FirestorePaths.HOTELS)
            .document(normalizedHotel)
            .collection(FirestorePaths.DAILY_AGENDA)

        if (replace) {
            val existing = col.get().await()
            val deleteBatch = firestore.batch()
            existing.documents.forEach { deleteBatch.delete(it.reference) }
            if (!existing.isEmpty) deleteBatch.commit().await()
        }

        var written = 0
        var batch = firestore.batch()
        var ops = 0
        parsed.items.forEachIndexed { index, session ->
            val ref = col.document()
            val payload = hashMapOf<String, Any>(
                "time" to session.time,
                "title" to session.title,
                "location" to session.location,
                "venue" to session.location,
                "date" to session.date,
                "notes" to session.notes,
                "sortOrder" to index,
                "source" to "docx_import",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            batch.set(ref, payload, SetOptions.merge())
            ops++
            written++
            if (ops >= 400) {
                batch.commit().await()
                batch = firestore.batch()
                ops = 0
            }
        }
        if (ops > 0) batch.commit().await()
        return written
    }
}
