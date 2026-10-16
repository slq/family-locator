package pl.slqpl.rodzina.location

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import pl.slqpl.rodzina.FamilyLocatorApp
import pl.slqpl.rodzina.data.LocationDatabase
import pl.slqpl.rodzina.data.LocationSample
import java.util.UUID

class TrackingSessionStoreTest {
    @Test fun atomicSessionRoundTripCorruptionAndPermanentStop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse((context.applicationContext as FamilyLocatorApp).tracking.status.value.running)
        val prefs = context.getSharedPreferences("tracking_session", Context.MODE_PRIVATE)
        val previous = prefs.getString("session", null)
        val disabled = prefs.getBoolean("disabled", false)
        val store = TrackingSessionStore(context)
        try {
            assertTrue(store.beginLocalStart())
            val request = TrackingRequest("alice", "command", 1_000_000, TrackingMode.LIVE)
            val saved = TrackingSession("bob", TrackingMode.LIVE, 901_000, 1_000, 7,
                RemoteTrackingState(500, request.id, request.requestedAt, setOf("older", request.id)), request, request)
            assertTrue(store.write(saved))
            assertEquals(saved, TrackingSessionStore(context).read())
            assertTrue(store.disable())
            assertNull(TrackingSessionStore(context).read())
            // A delayed heartbeat or auth callback must not undo an explicit stop.
            assertFalse(store.write(saved))
            assertTrue(store.beginLocalStart())
            assertTrue(store.write(saved))
            assertTrue(prefs.edit().putString("session", "{broken").commit())
            assertNull(store.read())
            assertTrue(prefs.edit().putString("session", "{}").commit())
            assertNull(store.read())
        } finally {
            prefs.edit().clear().apply {
                if (previous != null) putString("session", previous)
                putBoolean("disabled", disabled)
            }.commit()
        }
    }

    @Test fun offlineQueueSurvivesDatabaseReopenAndPauseDiscardRemainsOwnerScoped() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "recovery-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, LocationDatabase::class.java, name).build()
        var room = open()
        val point = LocationSample(ownerUid = "bob", latitude = 52.0, longitude = 21.0,
            accuracyMeters = 10f, measuredAt = System.currentTimeMillis(), batteryPercent = 70)
        try {
            room.locations().insert(point)
            room.locations().insert(point.copy(ownerUid = "alice", pointKey = UUID.randomUUID().toString()))
            room.close()
            room = open()
            assertEquals(point.pointKey, room.locations().newestPending("bob")!!.pointKey)
            room.locations().discardPending("bob")
            room.close()
            room = open()
            assertNull(room.locations().newestPending("bob"))
            assertNotNull(room.locations().newestPending("alice"))
        } finally { room.close(); context.deleteDatabase(name) }
    }
}
