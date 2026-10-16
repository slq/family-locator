package pl.slqpl.rodzina.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import pl.slqpl.rodzina.BuildConfig
import pl.slqpl.rodzina.data.LocationSample
import pl.slqpl.rodzina.location.RemoteLiveRequest
import pl.slqpl.rodzina.location.TrackingMode
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CloudUser(val uid: String, val email: String)
data class FamilyPosition(
    val uid: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val measuredAt: Long,
    val receivedAt: Long,
    val batteryPercent: Int,
)

/** Firebase is optional; local diagnostic builds never initialize a cloud client. */
class FamilyCloud {
    val configured = BuildConfig.FIREBASE_CONFIGURED
    private val auth = if (configured) FirebaseAuth.getInstance() else null
    private val database by lazy { FirebaseDatabase.getInstance() }
    private val mutableUser = MutableStateFlow<CloudUser?>(null)
    private val knownFamilies = ConcurrentHashMap<String, String>()
    val user = mutableUser.asStateFlow()

    init {
        auth?.addAuthStateListener { source ->
            mutableUser.value = source.currentUser?.let { CloudUser(it.uid, it.email.orEmpty()) }
        }
    }

    suspend fun signIn(email: String, password: String) {
        checkNotNull(auth) { "Brak konfiguracji Firebase" }
            .signInWithEmailAndPassword(email.trim(), password).await()
    }
    fun signOut() { cancelPendingWrites(); knownFamilies.clear(); auth?.signOut() }
    fun cancelPendingWrites() {
        // A configuration downloaded before creating RTDB may lack its URL.
        // Stopping local tracking must still succeed in that case.
        if (configured) runCatching { database.purgeOutstandingWrites() }
    }

    private suspend fun familyId(uid: String): String {
        check(auth?.currentUser?.uid == uid) { "Sesja użytkownika wygasła" }
        return database.getReference("memberships/$uid/familyId").get().await()
            .getValue(String::class.java)?.takeIf { it.isNotBlank() }
            ?.also { knownFamilies[uid] = it }
            ?: error("Konto nie zostało przypisane do rodziny. Sprawdź konfigurację Firebase.")
    }

    private fun snapshots(query: Query): Flow<DataSnapshot> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(snapshot) }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }.conflate()

    private fun memberships(id: String) = database.getReference("memberships").orderByChild("familyId").equalTo(id)
    private fun serverOffset() = snapshots(database.getReference(".info/serverTimeOffset"))
        .map { (it.value as? Number)?.toLong() ?: 0L }

    fun observeFamily(uid: String): Flow<FamilySnapshot> = flow {
        val id = familyId(uid)
        emitAll(combine(snapshots(memberships(id)), snapshots(database.getReference("families/$id")), serverOffset()) { roster, family, offset ->
            val selections = family.child("memberIcons").children.mapNotNull { entry ->
                entry.getValue(String::class.java)?.let { entry.key.orEmpty() to it }
            }.toMap()
            val appearances = FamilyIcons.resolve(roster.children.mapNotNull { it.key }, selections)
            val members = roster.children.mapNotNull { entry ->
                val memberUid = entry.key ?: return@mapNotNull null
                val point = family.child("locations/$memberUid")
                val name = family.child("memberNames/$memberUid").getValue(String::class.java)
                    ?.takeIf { it.isNotBlank() } ?: point.child("label").getValue(String::class.java)
                    ?.takeIf { it.isNotBlank() } ?: "Członek ${memberUid.takeLast(6)}"
                val lat = point.child("latitude").getValue(Double::class.java)
                val lon = point.child("longitude").getValue(Double::class.java)
                val measured = point.child("measuredAt").getValue(Long::class.java)
                val position = if (lat == null || lon == null || measured == null) null else FamilyPosition(
                    memberUid, name, lat, lon, (point.child("accuracyMeters").value as? Number)?.toFloat() ?: 0f,
                    measured, point.long("receivedAt"), (point.child("batteryPercent").value as? Number)?.toInt() ?: -1)
                val device = family.child("devices/$memberUid").takeIf { it.exists() }?.let {
                    FamilyDevice(it.child("sharing").getValue(Boolean::class.java) == true,
                        it.child("mode").getValue(String::class.java).orEmpty(), it.long("updatedAt"), it.long("liveUntil"))
                }
                val request = parseRequest(family.child("liveRequests/$memberUid/$uid"), uid)
                val ack = family.child("liveAcks/$memberUid/$uid").takeIf { it.exists() }?.let {
                    LiveAcknowledgment(it.child("requestId").getValue(String::class.java).orEmpty(),
                        it.child("state").getValue(String::class.java).orEmpty(), it.long("expiresAt"), it.long("respondedAt"))
                }
                FamilyMember(memberUid, name, position, device, request, ack,
                    appearances.getValue(memberUid), FamilyIcon.fromId(selections[memberUid]))
            }
            FamilySnapshot(members.sortedWith(compareBy<FamilyMember> { it.uid != uid }.thenBy { it.name.lowercase() }), offset)
        })
    }

    suspend fun renameMember(uid: String, targetUid: String, name: String) {
        val trimmed = name.trim()
        require(trimmed.length in 1..40) { "Nazwa musi mieć od 1 do 40 znaków." }
        val id = familyId(uid)
        check(auth?.currentUser?.uid == uid)
        database.getReference("families/$id/memberNames/$targetUid").setValue(trimmed).await()
    }

    suspend fun requestLive(uid: String, targetUid: String) {
        val id = familyId(uid)
        check(auth?.currentUser?.uid == uid)
        database.getReference("families/$id/liveRequests/$targetUid/$uid").setValue(mapOf(
            "id" to UUID.randomUUID().toString(), "requestedAt" to ServerValue.TIMESTAMP,
        )).await()
    }

    suspend fun setOwnIcon(uid: String, icon: FamilyIcon?) {
        val id = familyId(uid)
        check(auth?.currentUser?.uid == uid)
        database.getReference("families/$id/memberIcons/$uid").setValue(icon?.id).await()
    }

    fun observeRemoteControl(uid: String): Flow<RemoteControl> = flow {
        val id = familyId(uid)
        val commands = snapshots(database.getReference("families/$id/liveRequests/$uid"))
        val connected = snapshots(database.getReference(".info/connected")).map { it.getValue(Boolean::class.java) == true }
        emitAll(combine(commands, snapshots(memberships(id)), serverOffset(), connected) { incoming, roster, offset, online ->
            val allowed = roster.children.mapNotNull { it.key }.toSet()
            RemoteControl(incoming.children.mapNotNull { request ->
                request.key?.takeIf { it in allowed }?.let { parseRequest(request, it) }
            }, offset, online)
        })
    }

    suspend fun registerDisconnect(uid: String) {
        val id = familyId(uid)
        database.getReference("families/$id/devices/$uid").onDisconnect()
            .setValue(devicePayload(false, TrackingMode.PERIODIC, 0)).await()
    }

    suspend fun publishDevice(uid: String, mode: TrackingMode, liveUntil: Long) {
        val id = familyId(uid)
        check(auth?.currentUser?.uid == uid)
        database.getReference("families/$id/devices/$uid").setValue(devicePayload(true, mode, liveUntil)).await()
    }

    suspend fun acknowledge(uid: String, requests: List<RemoteLiveRequest>, state: String) {
        if (requests.isEmpty()) return
        val id = familyId(uid)
        check(auth?.currentUser?.uid == uid)
        database.getReference("families/$id/liveAcks/$uid").updateChildren(
            requests.associate { it.requesterUid to acknowledgment(it, state) }).await()
    }

    /** Best effort on an explicit stop; disconnect handling and heartbeat age also mark unavailable devices. */
    fun stopDevice(uid: String, requests: List<RemoteLiveRequest>) {
        val id = knownFamilies[uid] ?: return
        if (auth?.currentUser?.uid != uid) return
        // A replaced request may reject its old acknowledgment. Never let it reject the device stop too.
        database.getReference("families/$id/devices/$uid").setValue(devicePayload(false, TrackingMode.PERIODIC, 0))
        if (requests.isNotEmpty()) database.getReference("families/$id/liveAcks/$uid").updateChildren(
            requests.associate { it.requesterUid to acknowledgment(it, "stopped") })
    }

    private fun devicePayload(sharing: Boolean, mode: TrackingMode, liveUntil: Long): Map<String, Any> = mapOf(
        "sharing" to sharing, "mode" to if (sharing) mode.name.lowercase() else "stopped",
        "updatedAt" to ServerValue.TIMESTAMP, "liveUntil" to liveUntil,
    )
    private fun acknowledgment(request: RemoteLiveRequest, state: String): Map<String, Any> = mapOf(
        "requestId" to request.id, "state" to state, "respondedAt" to ServerValue.TIMESTAMP,
        "expiresAt" to request.expiresAt,
    )
    private fun parseRequest(snapshot: DataSnapshot, requesterUid: String): RemoteLiveRequest? {
        val requestId = snapshot.child("id").getValue(String::class.java) ?: return null
        val time = snapshot.child("requestedAt").getValue(Long::class.java) ?: return null
        return RemoteLiveRequest(requesterUid, requestId, time)
    }
    private fun DataSnapshot.long(name: String) = child(name).getValue(Long::class.java) ?: 0L

    suspend fun upload(sample: LocationSample, label: String) {
        val uid = requireNotNull(sample.ownerUid)
        val id = familyId(uid)
        // Check again after the async membership lookup; never upload under a different account.
        check(auth?.currentUser?.uid == uid)
        val ref = database.getReference("families/$id/locations/$uid")
        val payload = mapOf(
            "label" to label.take(40),
            "latitude" to sample.latitude, "longitude" to sample.longitude,
            "accuracyMeters" to sample.accuracyMeters,
            "measuredAt" to sample.measuredAt,
            "receivedAt" to ServerValue.TIMESTAMP,
            "batteryPercent" to sample.batteryPercent,
        )
        suspendCancellableCoroutine<Unit> { continuation ->
            ref.runTransaction(object : Transaction.Handler {
                override fun doTransaction(current: MutableData): Transaction.Result {
                    if (!continuation.isActive) return Transaction.abort()
                    val existing = current.child("measuredAt").getValue(Long::class.java) ?: 0
                    if (existing >= sample.measuredAt) return Transaction.abort()
                    current.value = payload
                    return Transaction.success(current)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                    if (!continuation.isActive) return
                    if (error != null) continuation.resumeWithException(error.toException())
                    else continuation.resume(Unit) // A newer server sample also acknowledges this point.
                }
            }, false)
        }
    }
}
