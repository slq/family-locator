package pl.slqpl.rodzina.location

import android.Manifest
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import pl.slqpl.rodzina.FamilyLocatorApp
import pl.slqpl.rodzina.MainActivity
import pl.slqpl.rodzina.cloud.FamilyCloud
import pl.slqpl.rodzina.cloud.TrackingRequestState
import pl.slqpl.rodzina.data.LocationDatabase
import pl.slqpl.rodzina.data.LocationSample
import java.net.HttpURLConnection
import java.net.URL

/** Opt-in only. Uses named Firebase apps, local emulators and in-memory Room, never production data. */
class TrackingControlTest {
    @Test(timeout = 120_000) fun remoteModesPauseAndResumeInBackgroundWithoutReplayingOldCommands() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("firebaseEmulators") == "true")
        val context = instrumentation.targetContext
        val app = context.applicationContext as FamilyLocatorApp
        assertFalse("Start this test with the sharing service stopped", app.tracking.status.value.running)
        val clients = mutableListOf<FirebaseDatabase>()
        val room = Room.inMemoryDatabaseBuilder(context, LocationDatabase::class.java).build()
        var activity: ActivityScenario<MainActivity>? = null
        val service = Intent(context, LocationSharingService::class.java)
        try {
            fun client(name: String): Pair<FirebaseAuth, FirebaseDatabase> {
                val options = FirebaseOptions.Builder().setProjectId("demo-family-locator")
                    .setApplicationId("1:1234567890:android:0123456789abcdef")
                    .setApiKey("AIzaSyEmulatorOnlyTestKey000000000000000")
                    .setDatabaseUrl("https://demo-family-locator-default-rtdb.firebaseio.com").build()
                val firebase = FirebaseApp.initializeApp(context, options, "$name-${System.nanoTime()}")
                return FirebaseAuth.getInstance(firebase).apply { useEmulator("10.0.2.2", 9099) } to
                    FirebaseDatabase.getInstance(firebase).apply { useEmulator("10.0.2.2", 9000); clients += this }
            }
            val (bobAuth, bobDatabase) = client("receiver")
            val (aliceAuth, aliceDatabase) = client("sender")
            val suffix = System.nanoTime()
            val bob = bobAuth.createUserWithEmailAndPassword("receiver-$suffix@example.test", "emulator-password").await().user!!.uid
            val alice = aliceAuth.createUserWithEmailAndPassword("sender-$suffix@example.test", "emulator-password").await().user!!.uid
            // The emulator's fixed admin credential seeds test membership only on the host loopback alias.
            val connection = URL("http://10.0.2.2:9000/memberships.json?ns=demo-family-locator-default-rtdb").openConnection() as HttpURLConnection
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 5_000; connection.readTimeout = 5_000
            connection.outputStream.use { it.write(JSONObject().put(bob, JSONObject().put("familyId", "test-family"))
                .put(alice, JSONObject().put("familyId", "test-family")).toString().toByteArray()) }
            try { assertEquals(200, connection.responseCode) } finally { connection.disconnect() }
            val receiver = FamilyCloud(bobAuth) { bobDatabase }
            val sender = FamilyCloud(aliceAuth) { aliceDatabase }
            app.testCloud = receiver; app.testDatabase = room
            for (permission in listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS)) {
                instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission").close()
            }
            activity = ActivityScenario.launch(MainActivity::class.java)
            fun local(action: String) { ContextCompat.startForegroundService(context, Intent(service).setAction(action)) }
            suspend fun ready(mode: TrackingMode) {
                android.util.Log.i("TrackingControlTest", "Waiting for local $mode")
                var observed = "No family snapshot"
                try { withTimeout(15_000) {
                receiver.observeFamily(bob).first { snapshot -> snapshot.members.any { member ->
                    if (member.uid == bob) observed = "device=${member.device}"
                    member.uid == bob && member.device?.controllable(System.currentTimeMillis() + snapshot.serverTimeOffset) == true &&
                        member.device.mode == mode.wireName
                } }
                } } catch (timeout: TimeoutCancellationException) {
                    throw AssertionError("Local $mode timed out: ${app.tracking.status.value}; $observed", timeout)
                }
                assertEquals(mode, app.tracking.status.value.mode)
                android.util.Log.i("TrackingControlTest", "Local $mode ready")
            }
            suspend fun remote(mode: TrackingMode) {
                android.util.Log.i("TrackingControlTest", "Sending remote $mode")
                sender.requestTracking(alice, bob, mode)
                var observed = "No family snapshot"
                try { withTimeout(15_000) {
                    sender.observeFamily(alice).first { snapshot -> snapshot.members.any { member ->
                        if (member.uid == bob) observed = "request=${member.request}, ack=${member.acknowledgment}, device=${member.device}"
                        member.uid == bob && member.request?.mode == mode &&
                            member.requestState(System.currentTimeMillis() + snapshot.serverTimeOffset) == TrackingRequestState.CONFIRMED
                    } }
                } } catch (timeout: TimeoutCancellationException) {
                    throw AssertionError("Remote $mode timed out: ${app.tracking.status.value}; $observed", timeout)
                }
                assertEquals(mode, app.tracking.status.value.mode)
                android.util.Log.i("TrackingControlTest", "Remote $mode confirmed")
            }
            local(LocationSharingService.ACTION_PAUSE)
            ready(TrackingMode.STOPPED)
            activity.moveToState(Lifecycle.State.CREATED)
            remote(TrackingMode.PERIODIC)
            remote(TrackingMode.LIVE)
            assertTrue(app.tracking.status.value.remainingSeconds in 1..900)
            val persisted = TrackingSessionStore(context).read()!!
            assertEquals(TrackingMode.LIVE, persisted.mode)
            assertEquals(persisted.activeRemote, receiver.verifyRemoteRequest(bob))
            assertEquals(persisted.remote, RemoteTrackingPolicy(persisted.remote).snapshot())
            // A queued offline sample must be discarded when remote sharing is disabled.
            room.locations().insert(LocationSample(ownerUid = bob, latitude = 52.0, longitude = 21.0,
                accuracyMeters = 10f, measuredAt = System.currentTimeMillis(), batteryPercent = 70))
            remote(TrackingMode.STOPPED)
            assertTrue(app.tracking.status.value.running)
            assertFalse(app.tracking.status.value.sharing)
            assertNull(room.locations().newestPending(bob))
            remote(TrackingMode.PERIODIC)
            activity.moveToState(Lifecycle.State.RESUMED)
            local(LocationSharingService.ACTION_PAUSE)
            ready(TrackingMode.STOPPED)
            bobDatabase.goOffline()
            delay(300)
            bobDatabase.goOnline()
            ready(TrackingMode.STOPPED)
            assertEquals(TrackingMode.STOPPED, app.tracking.status.value.mode)
            LocationSharingService.stop(context)
            withTimeout(5_000) { app.tracking.status.first { !it.running } }
            sender.requestTracking(alice, bob, TrackingMode.LIVE)
            assertFalse(app.tracking.status.value.running)
            local(LocationSharingService.ACTION_PAUSE)
            ready(TrackingMode.STOPPED)
            assertEquals(TrackingMode.STOPPED, app.tracking.status.value.mode)
            // A fresh command still works after the old one was ignored on explicit restart.
            remote(TrackingMode.PERIODIC)
        } finally {
            LocationSharingService.stop(context)
            withTimeoutOrNull(5_000) { app.tracking.status.first { !it.running } }
            activity?.close()
            app.testCloud = null; app.testDatabase = null
            clients.forEach { it.goOffline() }
            room.close()
        }
    }
}
