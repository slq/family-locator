package pl.slqpl.rodzina.location

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import pl.slqpl.rodzina.cloud.*
import pl.slqpl.rodzina.data.LocationDatabase
import pl.slqpl.rodzina.data.LocationSample
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId

class HistoryIntegrationTest {
    @Test fun upgradingTheOfflineQueuePreservesEveryPointAndItsStableIdentity() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "history-migration-${System.nanoTime()}.db"
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { legacy ->
            legacy.execSQL("CREATE TABLE samples (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, ownerUid TEXT, latitude REAL NOT NULL, longitude REAL NOT NULL, accuracyMeters REAL NOT NULL, measuredAt INTEGER NOT NULL, batteryPercent INTEGER NOT NULL, uploaded INTEGER NOT NULL)")
            for (time in listOf(1000L, 2000L)) legacy.execSQL("INSERT INTO samples(ownerUid,latitude,longitude,accuracyMeters,measuredAt,batteryPercent,uploaded) VALUES ('alice',52,21,10,?,70,0)", arrayOf(time))
            legacy.version = 1
        }
        val room = Room.databaseBuilder(context, LocationDatabase::class.java, name)
            .addMigrations(LocationDatabase.MIGRATION_1_2).build()
        try {
            val dao = room.locations()
            val before = dao.pending("alice", 200)
            assertEquals(listOf(1000L, 2000L), before.map { it.measuredAt })
            assertEquals(2, before.map { it.pointKey }.distinct().size)
            assertTrue(before.all { it.pointKey.length == 32 })
            assertEquals(before, dao.pending("alice", 200))
            dao.insert(before.first().copy(id = 0, ownerUid = "bob"))
            dao.acknowledge("alice", before.first().id)
            assertEquals(listOf(before.last()), dao.pending("alice", 200))
            dao.deleteThrough("alice", 2000)
            assertTrue(dao.pending("alice", 200).isEmpty())
            assertEquals(1, dao.pending("bob", 200).size)
        } finally { room.close(); context.deleteDatabase(name) }
    }

    /** Local emulator accounts only. No application override or real family data is touched. */
    @Test(timeout = 120_000) fun archivesOfflinePointsViewsAnotherMemberAndDeletesInclusivelyWithoutResurrection() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("firebaseEmulators") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val apps = mutableListOf<FirebaseApp>()
        val databases = mutableListOf<FirebaseDatabase>()
        fun admin(path: String, method: String = "GET", payload: JSONObject? = null): JSONObject {
            val connection = URL("http://10.0.2.2:9000/$path.json?ns=demo-family-locator-default-rtdb").openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer owner")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 5000; connection.readTimeout = 5000
            if (payload != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            }
            try {
                assertEquals(200, connection.responseCode)
                return JSONObject(connection.inputStream.bufferedReader().use { it.readText() }.takeUnless { it == "null" } ?: "{}")
            } finally { connection.disconnect() }
        }
        try {
            fun client(name: String): Pair<FirebaseAuth, FirebaseDatabase> {
                val options = FirebaseOptions.Builder().setProjectId("demo-family-locator")
                    .setApplicationId("1:1234567890:android:0123456789abcdef")
                    .setApiKey("AIzaSyEmulatorOnlyTestKey000000000000000")
                    .setDatabaseUrl("https://demo-family-locator-default-rtdb.firebaseio.com").build()
                val firebase = FirebaseApp.initializeApp(context, options, "history-$name-${System.nanoTime()}")
                apps += firebase
                return FirebaseAuth.getInstance(firebase).apply { useEmulator("10.0.2.2", 9099) } to
                    FirebaseDatabase.getInstance(firebase).apply { useEmulator("10.0.2.2", 9000); databases += this }
            }
            val (bobAuth, bobDb) = client("bob")
            val (aliceAuth, aliceDb) = client("alice")
            val suffix = System.nanoTime()
            val bob = bobAuth.createUserWithEmailAndPassword("history-bob-$suffix@example.test", "emulator-password").await().user!!.uid
            val alice = aliceAuth.createUserWithEmailAndPassword("history-alice-$suffix@example.test", "emulator-password").await().user!!.uid
            val family = "history-test-$suffix"
            admin("memberships", "PATCH", JSONObject().put(bob, JSONObject().put("familyId", family))
                .put(alice, JSONObject().put("familyId", family)))
            val owner = FamilyCloud(bobAuth) { bobDb }
            val viewer = FamilyCloud(aliceAuth) { aliceDb }
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val today = HistoryPolicy.day(now, zone)
            val yesterday = today.minusDays(1)
            val midnight = today.atStartOfDay(zone).toInstant().toEpochMilli()
            fun point(time: Long, lat: Double = 52.0) = LocationSample(ownerUid = bob, latitude = lat,
                longitude = 21.0, accuracyMeters = 10f, measuredAt = time, batteryPercent = 70)
            val first = point(yesterday.atStartOfDay(zone).toInstant().toEpochMilli() + 1)
            val last = point(midnight - 1, 52.01)
            val fresh = point(now, 52.02)
            val points = listOf(first, last, fresh)
            owner.history.store(owner.history.uploadContext(bob), points)
            owner.history.store(owner.history.uploadContext(bob), points) // Retry has the same IDs, no duplicates.
            suspend fun route(day: java.time.LocalDate) = withTimeout(15_000) {
                viewer.history.observe(alice, HistorySelection(bob, day), zone).first { !it.loading }.also { assertEquals("", it.error) }
            }
            assertEquals(listOf(first.pointKey, last.pointKey), route(yesterday).points.map { it.id })
            assertEquals(listOf(fresh.pointKey), route(today).points.map { it.id })
            assertFalse(admin("families/$family").has("history"))
            owner.upload(last, "Bob")
            val cutoff = withTimeout(20_000) { owner.history.deleteThrough(bob, bob, yesterday, zone) }
            assertEquals(midnight - 1, cutoff)
            assertTrue(route(yesterday).points.isEmpty())
            assertEquals(listOf(fresh.pointKey), route(today).points.map { it.id })
            val familyState = withTimeout(10_000) { viewer.observeFamily(alice).first { it.members.any { member -> member.uid == bob } } }
            assertNull(familyState.members.first { it.uid == bob }.position)
            assertFalse(admin("history/$family/$bob").has(last.pointKey))
            // A retry from a phone which missed the deletion is rejected by server rules.
            val stale = HistoryUploadContext(family, bob, 0, System.currentTimeMillis())
            try { owner.history.store(stale, listOf(last)); fail("Deleted point was restored") }
            catch (_: com.google.firebase.database.DatabaseException) { }
            try { viewer.history.deleteThrough(alice, bob, yesterday, zone); fail("Another member deleted Bob's data") }
            catch (_: IllegalArgumentException) { }
            // Exercise paginated deletion and automatic cleanup of a departed member's expired points.
            val oldTime = now - 31 * 86_400_000L
            val expired = JSONObject().put("latitude", 52).put("longitude", 21).put("accuracyMeters", 10)
                .put("measuredAt", oldTime).put("receivedAt", now).put("batteryPercent", 70)
            val payload = JSONObject()
            repeat(1003) { payload.put("history/$family/gone/p$it", expired) }
            payload.put("families/$family/historyOwners/gone", true)
            admin("", "PATCH", payload)
            withTimeout(20_000) { viewer.history.maintain(alice) }
            assertEquals(0, admin("history/$family/gone").length())
            assertEquals(1, admin("history/$family/$bob").length())
        } finally {
            databases.forEach { it.goOffline() }
            apps.forEach { it.delete() }
        }
    }
}
