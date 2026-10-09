package pl.slawek.rodzina.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import pl.slawek.rodzina.BuildConfig
import pl.slawek.rodzina.data.LocationSample
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
    fun signOut() { cancelPendingWrites(); auth?.signOut() }
    fun cancelPendingWrites() {
        // A configuration downloaded before creating RTDB may lack its URL.
        // Stopping local tracking must still succeed in that case.
        if (configured) runCatching { database.purgeOutstandingWrites() }
    }

    private suspend fun familyId(uid: String): String {
        check(auth?.currentUser?.uid == uid) { "Sesja użytkownika wygasła" }
        return database.getReference("memberships/$uid/familyId").get().await()
            .getValue(String::class.java)?.takeIf { it.isNotBlank() }
            ?: error("Konto nie zostało przypisane do rodziny. Sprawdź konfigurację Firebase.")
    }

    fun observeFamily(uid: String): Flow<List<FamilyPosition>> = flow {
        val id = familyId(uid)
        emitAll(callbackFlow {
            val ref = database.getReference("families/$id/locations")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val positions = snapshot.children.mapNotNull { child ->
                        val lat = child.child("latitude").getValue(Double::class.java)
                        val lon = child.child("longitude").getValue(Double::class.java)
                        val measured = child.child("measuredAt").getValue(Long::class.java)
                        if (lat == null || lon == null || measured == null) null else FamilyPosition(
                            uid = child.key.orEmpty(),
                            label = child.child("label").getValue(String::class.java).orEmpty(),
                            latitude = lat, longitude = lon,
                            accuracyMeters = (child.child("accuracyMeters").value as? Number)?.toFloat() ?: 0f,
                            measuredAt = measured,
                            receivedAt = child.child("receivedAt").getValue(Long::class.java) ?: 0,
                            batteryPercent = (child.child("batteryPercent").value as? Number)?.toInt() ?: -1,
                        )
                    }
                    trySend(positions)
                }
                override fun onCancelled(error: DatabaseError) { close(error.toException()) }
            }
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        })
    }

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
