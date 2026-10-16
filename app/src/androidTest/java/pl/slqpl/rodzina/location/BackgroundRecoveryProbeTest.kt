package pl.slqpl.rodzina.location

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import pl.slqpl.rodzina.FamilyLocatorApp
import pl.slqpl.rodzina.data.LocationSample
import java.util.UUID

/** The host kills the real debug process between seed and check; only an unsigned-in emulator is allowed. */
class BackgroundRecoveryProbeTest {
    @Test fun durableQueueProbe() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("backgroundRecoveryProbe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as FamilyLocatorApp
        val auth = withTimeout(15_000) { app.cloud.authState.first { it.ready } }
        assertNull("Use an emulator without a signed-in default account", auth.uid)
        val prefs = context.getSharedPreferences("recovery_probe", Context.MODE_PRIVATE)
        val dao = app.database.locations()
        if (args.getString("phase") == "seed") {
            assertFalse(app.tracking.status.value.running)
            assertNull("Clean up the previous probe first", prefs.getString("uid", null))
            val uid = "recovery-probe-${UUID.randomUUID()}"
            val point = LocationSample(ownerUid = uid, latitude = 52.0, longitude = 21.0,
                accuracyMeters = 10f, measuredAt = System.currentTimeMillis(), batteryPercent = 70)
            dao.insert(point)
            assertTrue(prefs.edit().putString("uid", uid).putString("key", point.pointKey).commit())
        } else {
            val uid = prefs.getString("uid", null) ?: return@runBlocking
            try {
                if (args.getString("phase") == "check") {
                    assertEquals(prefs.getString("key", null), dao.newestPending(uid)?.pointKey)
                    assertEquals(1, dao.pending(uid, 10).size)
                    assertFalse("Failed account recovery must not mark this queue for discarding",
                        context.getSharedPreferences("queue_policy", Context.MODE_PRIVATE).getBoolean(uid, false))
                    assertNull("Explicit stop must disable recovery", TrackingSessionStore(context).read())
                }
            } finally {
                LocationSharingService.stop(context)
                dao.deleteThrough(uid, Long.MAX_VALUE)
                prefs.edit().clear().commit()
            }
        }
    }
}
