package pl.slqpl.rodzina.cloud

import com.google.firebase.database.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import pl.slqpl.rodzina.data.LocationSample
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class HistoryUploadContext(val familyId: String, val uid: String, val deletedThrough: Long, val serverNow: Long)

/** History lives outside families: opening the current map never downloads old routes. */
@OptIn(ExperimentalCoroutinesApi::class)
class FamilyHistory(
    private val database: FirebaseDatabase,
    private val familyId: suspend (String) -> String,
    private val currentUid: () -> String?,
) {
    private fun requireUser(uid: String) { check(currentUid() == uid) { "Sesja użytkownika wygasła" } }
    private fun snapshots(query: Query): Flow<DataSnapshot> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(snapshot) }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }.conflate()

    private suspend fun now(): Long = System.currentTimeMillis() +
        ((snapshots(database.getReference(".info/serverTimeOffset")).first().value as? Number)?.toLong() ?: 0)

    suspend fun uploadContext(uid: String): HistoryUploadContext {
        val id = familyId(uid)
        val deleted = database.getReference("families/$id/historyDeletedThrough/$uid").get().await()
            .getValue(Long::class.java) ?: 0
        requireUser(uid)
        return HistoryUploadContext(id, uid, deleted, now())
    }

    suspend fun store(context: HistoryUploadContext, samples: List<LocationSample>) {
        require(samples.all { it.ownerUid == context.uid && it.pointKey.isNotBlank() })
        requireUser(context.uid)
        val accepted = samples.filter { HistoryPolicy.acceptable(it, context.serverNow, context.deletedThrough) }
        if (accepted.isEmpty()) return
        val updates = accepted.associate { sample ->
            "history/${context.familyId}/${context.uid}/${sample.pointKey}" to mapOf(
                "latitude" to sample.latitude, "longitude" to sample.longitude, "accuracyMeters" to sample.accuracyMeters,
                "measuredAt" to sample.measuredAt, "receivedAt" to ServerValue.TIMESTAMP, "batteryPercent" to sample.batteryPercent,
            )
        }.toMutableMap<String, Any?>()
        updates["families/${context.familyId}/historyOwners/${context.uid}"] = true
        database.reference.updateChildren(updates).await()
    }

    fun observe(uid: String, selection: HistorySelection, zone: ZoneId = ZoneId.systemDefault()): Flow<HistorySnapshot> = flow {
        val id = familyId(uid)
        requireUser(uid)
        emitAll(snapshots(database.getReference("families/$id/historyDeletedThrough/${selection.uid}"))
            .flatMapLatest { boundary ->
                val deleted = boundary.getValue(Long::class.java) ?: 0L
                flow {
                    val serverNow = now()
                    require(selection.day <= HistoryPolicy.day(serverNow, zone) &&
                        selection.day >= HistoryPolicy.day(serverNow - HistoryPolicy.RETENTION_MILLIS, zone))
                    val range = HistoryPolicy.range(selection.day, serverNow, deleted, zone)
                    if (range.empty) emit(HistorySnapshot(selection, deletedThrough = deleted))
                    else {
                        val query = database.getReference("history/$id/${selection.uid}").orderByChild("measuredAt")
                            // Timestamps are integer milliseconds; half-ms bounds preserve inclusive endpoints
                            // and keep query numbers as doubles across SDKs and the database emulator.
                            .startAt(range.start.toDouble() - 0.5).endAt(range.endInclusive.toDouble() + 0.5)
                            .limitToFirst(HistoryPolicy.MAX_DAY_POINTS)
                        emitAll(snapshots(query).map { snapshot ->
                            requireUser(uid)
                            val cutoff = maxOf(deleted, serverNow - HistoryPolicy.RETENTION_MILLIS - 1)
                            val points = snapshot.children.mapNotNull { parse(it) }.filter { it.measuredAt > cutoff }
                                .sortedWith(compareBy<HistoryPoint> { it.measuredAt }.thenBy { it.id })
                            HistorySnapshot(selection, points, deletedThrough = deleted)
                        })
                    }
                }.onStart { emit(HistorySnapshot(selection, deletedThrough = deleted, loading = true)) }
                    .catch { error ->
                        if (error is CancellationException) throw error
                        emit(HistorySnapshot(selection, deletedThrough = deleted,
                            error = "Nie udało się pobrać historii. Sprawdź internet, członkostwo i aktualne reguły Firebase, a następnie odśwież."))
                    }
            })
    }

    /** The monotonic watermark is committed first, so offline points cannot restore a deleted day. */
    suspend fun deleteThrough(uid: String, targetUid: String, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long {
        require(uid == targetUid) { "Możesz usunąć tylko własną historię" }
        val id = familyId(uid)
        val cutoff = HistoryPolicy.deletionCutoff(date, now(), zone)
        requireUser(uid)
        val effective = advanceBoundary(database.getReference("families/$id/historyDeletedThrough/$targetUid"), cutoff)
        requireUser(uid)
        deletePages(uid, id, targetUid, effective, Int.MAX_VALUE)
        deleteLastPosition(uid, id, targetUid, effective)
        return effective
    }

    /** Bounded maintenance also removes histories belonging to members who have left the family. */
    suspend fun maintain(uid: String) {
        val id = familyId(uid)
        val expired = now() - HistoryPolicy.RETENTION_MILLIS - 2_000
        val owners = database.getReference("families/$id/historyOwners").get().await().children.mapNotNull { it.key }
        for (owner in owners) {
            requireUser(uid)
            val deleted = if (owner == uid) database.getReference("families/$id/historyDeletedThrough/$owner")
                .get().await().getValue(Long::class.java) ?: 0 else 0
            deletePages(uid, id, owner, maxOf(expired, deleted), 5)
        }
    }

    private suspend fun deletePages(uid: String, family: String, target: String, through: Long, maxPages: Int) {
        val ref = database.getReference("history/$family/$target")
        repeat(maxPages) {
            requireUser(uid)
            val page = ref.orderByChild("measuredAt").endAt(through.toDouble() + 0.5)
                .limitToFirst(HistoryPolicy.CLEANUP_PAGE_SIZE).get().await()
            requireUser(uid)
            val updates = page.children.mapNotNull { it.key }.associateWith { null }
            if (updates.isEmpty()) return
            ref.updateChildren(updates).await()
            if (updates.size < HistoryPolicy.CLEANUP_PAGE_SIZE) return
        }
    }

    private suspend fun advanceBoundary(ref: DatabaseReference, cutoff: Long): Long = suspendCancellableCoroutine { continuation ->
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                if (!continuation.isActive) return Transaction.abort()
                val existing = data.getValue(Long::class.java) ?: 0
                if (existing >= cutoff) return Transaction.abort()
                data.value = cutoff
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (!continuation.isActive) return
                if (error != null) continuation.resumeWithException(error.toException())
                else continuation.resume(snapshot?.getValue(Long::class.java) ?: cutoff)
            }
        }, false)
    }

    private suspend fun deleteLastPosition(uid: String, family: String, target: String, through: Long): Unit = suspendCancellableCoroutine { continuation ->
        database.getReference("families/$family/locations/$target").runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                if (!continuation.isActive || currentUid() != uid) return Transaction.abort()
                val time = data.child("measuredAt").getValue(Long::class.java) ?: return Transaction.abort()
                if (time > through) return Transaction.abort()
                data.value = null
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (!continuation.isActive) return
                if (error != null) continuation.resumeWithException(error.toException()) else continuation.resume(Unit)
            }
        }, false)
    }

    private fun parse(snapshot: DataSnapshot): HistoryPoint? {
        val point = HistoryPoint(snapshot.key ?: return null,
            snapshot.child("latitude").getValue(Double::class.java) ?: return null,
            snapshot.child("longitude").getValue(Double::class.java) ?: return null,
            (snapshot.child("accuracyMeters").value as? Number)?.toFloat() ?: return null,
            snapshot.child("measuredAt").getValue(Long::class.java) ?: return null,
            (snapshot.child("batteryPercent").value as? Number)?.toInt() ?: -1)
        return point.takeIf { it.latitude.isFinite() && it.latitude in -90.0..90.0 &&
            it.longitude.isFinite() && it.longitude in -180.0..180.0 && it.accuracyMeters.isFinite() && it.accuracyMeters > 0 }
    }
}
