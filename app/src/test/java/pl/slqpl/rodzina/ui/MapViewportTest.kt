package pl.slqpl.rodzina.ui

import org.junit.Assert.*
import org.junit.Test
import pl.slqpl.rodzina.cloud.FamilyMember
import pl.slqpl.rodzina.cloud.FamilyPosition
import pl.slqpl.rodzina.cloud.FamilySnapshot
import pl.slqpl.rodzina.data.LocationSample

class MapViewportTest {
    private val alice = MapPoint("alice", 52.0, 21.0)
    private val bob = MapPoint("bob", 50.0, 19.0)
    private fun snapshot(vararg points: MapPoint) = FamilySnapshot(points.map {
        FamilyMember(it.uid, it.uid, FamilyPosition(it.uid, it.uid, it.latitude, it.longitude, 10f, 1_000, 1_000, 70), null, null, null)
    })
    private val oldLocal = LocationSample(ownerUid = "alice", latitude = 37.0, longitude = -122.0,
        accuracyMeters = 10f, measuredAt = 500, batteryPercent = 70)

    @Test fun `overview includes everyone without letting old local coordinates widen the family view`() {
        assertEquals(listOf(alice, bob), mapPoints(snapshot(bob, alice), oldLocal, "alice"))
        assertEquals(listOf(alice, bob), FamilyMapView().targets(listOf(alice, bob)))
    }

    @Test fun `choosing a person focuses only that person and follows their new coordinates`() {
        val view = FamilyMapView().focus("bob")
        assertTrue(view.following)
        assertEquals(listOf(bob), view.targets(listOf(alice, bob)))
        val moved = bob.copy(latitude = 51.0)
        assertEquals(listOf(moved), view.targets(listOf(alice, moved)))
        assertEquals(view.requestId + 1, view.focus("bob").requestId)
    }

    @Test fun `manual browsing pauses and resuming preserves the selected person`() {
        val view = FamilyMapView().focus("bob").pause()
        assertFalse(view.following)
        assertEquals("bob", view.memberUid)
        assertTrue(view.resume().following)
        assertEquals("bob", view.resume().memberUid)
        val all = view.showAll()
        assertNull(all.memberUid)
        assertTrue(all.following)
    }

    @Test fun `missing selected coordinates never redirect focus to a different person`() {
        val selected = FamilyMapView().focus("bob")
        assertTrue(selected.targets(listOf(alice)).isEmpty())
        assertEquals(selected, selected.reconcileMembers(emptySet())) // A transient loading snapshot.
        assertEquals(selected, selected.reconcileMembers(setOf("alice", "bob")))
        assertNull(selected.reconcileMembers(setOf("alice")).memberUid)
    }

    @Test fun `coincident points and empty families have safe viewport targets`() {
        assertEquals(1, FamilyMapView().targets(listOf(alice, alice.copy(uid = "bob"))).size)
        assertTrue(FamilyMapView().targets(emptyList()).isEmpty())
        assertTrue(mapPoints(FamilySnapshot(), null, "alice").isEmpty())
    }

    @Test fun `local fallback belongs to the current session and invalid coordinates are excluded`() {
        assertEquals(1, mapPoints(FamilySnapshot(), oldLocal, "alice").size)
        assertTrue(mapPoints(FamilySnapshot(), oldLocal, "bob").isEmpty())
        assertEquals(1, mapPoints(FamilySnapshot(), oldLocal.copy(ownerUid = null), null).size)
        assertTrue(mapPoints(snapshot(alice.copy(latitude = Double.NaN)), null, "alice").isEmpty())
    }
}
