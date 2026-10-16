package pl.slqpl.rodzina.cloud

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import pl.slqpl.rodzina.BuildConfig
import pl.slqpl.rodzina.data.LocationSample
import pl.slqpl.rodzina.location.TrackingRequest
import pl.slqpl.rodzina.location.TrackingMode
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CloudAuthState(val ready: Boolean, val uid: String?)

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
class FamilyCloud(
    private val auth: FirebaseAuth? = if (BuildConfig.FIREBASE_CONFIGURED) FirebaseAuth.getInstance() else null,
    databaseProvider: () -> FirebaseDatabase = { FirebaseDatabase.getInstance() },
) {
    val configured = auth != null
    private val database by lazy(databaseProvider)
    private val mutableUser = MutableStateFlow<CloudUser?>(null)
    private val knownFamilies = ConcurrentHashMap<String, String>()
    val user = mutableUser.asStateFlow()
    private val mutableAuthState = MutableStateFlow(CloudAuthState(auth == null, null))
    val authState = mutableAuthState.asStateFlow()
    val history by lazy { FamilyHistory(database, ::familyId) { auth?.currentUser?.uid } }

    init {
        auth?.addAuthStateListener { source ->
            mutableUser.value = source.currentUser?.let { CloudUser(it.uid, it.email.orEmpty()) }
            mutableAuthState.value = CloudAuthState(true, source.currentUser?.uid)
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
                val deletedThrough = family.child("historyDeletedThrough/$memberUid").getValue(Long::class.java) ?: 0L
                val position = if (lat == null || lon == null || measured == null || measured <= deletedThrough) null else FamilyPosition(
                    memberUid, name, lat, lon, (point.child("accuracyMeters").value as? Number)?.toFloat() ?: 0f,
                    measured, point.long("receivedAt"), (point.child("batteryPercent").value as? Number)?.toInt() ?: -1)
                val device = family.child("devices/$memberUid").takeIf { it.exists() }?.let {
                    FamilyDevice(it.child("sharing").getValue(Boolean::class.java) == true,
                        it.child("mode").getValue(String::class.java).orEmpty(), it.long("updatedAt"), it.long("liveUntil"),
                        it.child("controlReady").getValue(Boolean::class.java) == true)
                }
                val request = parseRequest(family.child("trackingRequests/$memberUid"))
                val ack = family.child("trackingAcks/$memberUid").takeIf { it.exists() }?.let {
                    TrackingAcknowledgment(it.child("requestId").getValue(String::class.java).orEmpty(),
                        it.child("mode").getValue(String::class.java).orEmpty(), it.long("respondedAt"))
                }
                FamilyMember(memberUid, name, position, device, request, ack,
                    appearances.getValue(memberUid), FamilyIcon.fromId(selections[memberUid]), deletedThrough)
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

    suspend fun requestTracking(uid: String, targetUid: String, mode: TrackingMode) {
        val id = familyId(uid)
        check(auth?.currentUser?.uid == uid)
        database.getReference("families/$id/trackingRequests/$targetUid").setValue(mapOf(
            "id" to UUID.randomUUID().toString(), "requesterUid" to uid,
            "mode" to mode.wireName, "requestedAt" to ServerValue.TIMESTAMP,
        )).await()
    }

    suspend fun setOwnIcon(uid: String, icon: FamilyIcon?) {
        val id = familyId(uid)
        check(auth?.currentUser?.uid == uid)
        database.getReference("families/$id/memberIcons/$uid").setValue(icon?.id).await()
    }

    fun observeRemoteControl(uid: String): Flow<RemoteControl> = flow {
        val id = familyId(uid)
        val commands = snapshots(database.getReference("families/$id/trackingRequests/$uid"))
        val connected = snapshots(database.getReference(".info/connected")).map { it.getValue(Boolean::class.java) == true }
        emitAll(combine(commands, snapshots(memberships(id)), serverOffset(), connected) { incoming, roster, offset, online ->
            val allowed = roster.children.mapNotNull { it.key }.toSet()
            RemoteControl(parseRequest(incoming)?.takeIf { it.requesterUid in allowed }, offset, online)
        })
    }

    /** Recovery must consult the server: RTDB get() may fall back to a cached value offline. */
    suspend fun verifyRemoteRequest(uid: String): TrackingRequest? {
        val source = checkNotNull(auth)
        check(source.currentUser?.uid == uid)
        val token = checkNotNull(source.currentUser?.getIdToken(false)?.await()?.token)
        val root = database.reference.toString().trimEnd('/')
        val namespace = Uri.parse(source.app.options.databaseUrl).host?.substringBefore('.')
        return withContext(Dispatchers.IO) {
            fun read(path: String, query: Map<String, String> = emptyMap()): JSONObject? {
                ensureActive()
                val uri = Uri.parse("$root/$path.json").buildUpon()
                    .appendQueryParameter("auth", token).apply {
                        if (namespace != null) appendQueryParameter("ns", namespace)
                        query.forEach { (key, value) -> appendQueryParameter(key, value) }
                    }.build()
                val connection = URL(uri.toString()).openConnection() as HttpURLConnection
                connection.connectTimeout = 3_000; connection.readTimeout = 3_000
                connection.instanceFollowRedirects = false
                try {
                    val status = connection.responseCode
                    check(status == 200) { "Nie zweryfikowano polecenia rodziny ($status)" }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    return if (body == "null") null else JSONObject(body)
                } finally { connection.disconnect() }
            }
            val family = read("memberships/${Uri.encode(uid)}")?.getString("familyId") ?: return@withContext null
            val value = read("families/${Uri.encode(family)}/trackingRequests/${Uri.encode(uid)}") ?: return@withContext null
            val requester = value.getString("requesterUid")
            val roster = read("memberships", mapOf("orderBy" to JSONObject.quote("familyId"),
                "equalTo" to JSONObject.quote(family))) ?: return@withContext null
            if (!roster.has(uid) || !roster.has(requester)) return@withContext null
            check(source.currentUser?.uid == uid)
            TrackingRequest(requester, value.getString("id"), value.getLong("requestedAt"),
                TrackingMode.fromWire(value.getString("mode")) ?: return@withContext null)
        }
    }

    suspend fun registerDisconnect(uid: String) {
        val id = familyId(uid)
        database.getReference("families/$id/devices/$uid").onDisconnect()
            .setValue(devicePayload(TrackingMode.STOPPED, 0, false)).await()
    }

    suspend fun publishDevice(uid: String, mode: TrackingMode, liveUntil: Long) {
        val id = familyId(uid)
        check(auth?.currentUser?.uid == uid)
        database.getReference("families/$id/devices/$uid").setValue(devicePayload(mode, liveUntil, true)).await()
    }

    suspend fun acknowledgeTracking(uid: String, request: TrackingRequest, liveUntil: Long) {
        val id = familyId(uid)
        check(auth?.currentUser?.uid == uid)
        // Atomic confirmation: the list never sees an acknowledgment without its device state.
        database.getReference("families/$id").updateChildren(mapOf(
            "trackingAcks/$uid" to mapOf("requestId" to request.id, "mode" to request.mode.wireName,
                "respondedAt" to ServerValue.TIMESTAMP),
            "devices/$uid" to devicePayload(request.mode, liveUntil, true),
        )).await()
    }

    /** Best effort on an explicit stop; disconnect handling and heartbeat age also mark unavailable devices. */
    fun stopDevice(uid: String) {
        val id = knownFamilies[uid] ?: return
        if (auth?.currentUser?.uid != uid) return
        database.getReference("families/$id/devices/$uid").setValue(devicePayload(TrackingMode.STOPPED, 0, false))
    }

    private fun devicePayload(mode: TrackingMode, liveUntil: Long, controlReady: Boolean): Map<String, Any> = mapOf(
        "sharing" to (mode != TrackingMode.STOPPED), "mode" to mode.wireName,
        "updatedAt" to ServerValue.TIMESTAMP, "liveUntil" to liveUntil, "controlReady" to controlReady,
    )
    private fun parseRequest(snapshot: DataSnapshot): TrackingRequest? {
        val requestId = snapshot.child("id").getValue(String::class.java) ?: return null
        val time = snapshot.child("requestedAt").getValue(Long::class.java) ?: return null
        val requester = snapshot.child("requesterUid").getValue(String::class.java) ?: return null
        val mode = TrackingMode.fromWire(snapshot.child("mode").getValue(String::class.java).orEmpty()) ?: return null
        return TrackingRequest(requester, requestId, time, mode)
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
