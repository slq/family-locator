package pl.slqpl.rodzina.location

import org.junit.Assert.*
import org.junit.Test

class RemoteTrackingPolicyTest {
    private val now = 1_000_000L
    private fun request(mode: TrackingMode = TrackingMode.LIVE, at: Long = now, id: String = "first") =
        TrackingRequest("alice", id, at, mode)

    @Test fun `all modes apply including stop followed by remote resume`() {
        val remote = RemoteTrackingPolicy(0)
        val tracking = TrackingPolicy()
        listOf(TrackingMode.LIVE, TrackingMode.STOPPED, TrackingMode.PERIODIC, TrackingMode.LIVE).forEachIndexed { i, mode ->
            val accepted = remote.accept(request(mode, now + i, "command-$i"), now + i, 1_000L + i)!!
            tracking.setMode(accepted.mode, 1_000L + i, accepted.expiresAt - (now + i))
            assertEquals(mode, tracking.mode(1_000L + i))
        }
    }

    @Test fun `a delayed command gets only the remainder of fifteen minutes`() {
        val command = request(at = now - 300_000)
        val accepted = RemoteTrackingPolicy(0).accept(command, now, 400_000)!!
        val tracking = TrackingPolicy()
        tracking.setMode(accepted.mode, 400_000, accepted.expiresAt - now)
        assertEquals(TrackingMode.LIVE, tracking.mode(999_999))
        assertEquals(TrackingMode.PERIODIC, tracking.mode(1_000_000))
    }

    @Test fun `duplicates and a backwards clock never restart the timer`() {
        val remote = RemoteTrackingPolicy(0)
        val command = request()
        assertNotNull(remote.accept(command, now, 1_000))
        assertNull(remote.accept(command, now - 60_000, 61_000))
        assertNull(RemoteTrackingPolicy(0, remote.consumedId).accept(command, now, 1_000))
    }

    @Test fun `local changes and a new service supersede earlier commands`() {
        val remote = RemoteTrackingPolicy(1_000)
        assertNull(remote.accept(request(at = now - 100), now, 1_010))
        assertNotNull(remote.accept(request(id = "new"), now, 1_010))
        remote.localChange(2_000)
        assertNull(remote.accept(request(at = now + 500, id = "delayed"), now + 1_000, 2_010))
        assertNotNull(remote.accept(request(at = now + 1_001, id = "after-local"), now + 1_001, 2_011))
    }

    @Test fun `expired future and out of order commands cannot change the mode`() {
        val remote = RemoteTrackingPolicy(0)
        assertNull(remote.accept(request(at = now - TrackingPolicy.LIVE_DURATION, id = "expired"), now, 1_000_000))
        assertNull(remote.accept(request(at = now + 6_000, id = "future"), now, 1_000))
        assertNotNull(remote.accept(request(), now, 1_000))
        assertNull(remote.accept(request(at = now - 1, id = "older"), now, 1_001))
        assertNull(remote.accept(request(), now, 1_002))
    }
}
