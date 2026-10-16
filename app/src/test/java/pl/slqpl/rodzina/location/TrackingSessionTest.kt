package pl.slqpl.rodzina.location

import org.junit.Assert.*
import org.junit.Test

class TrackingSessionTest {
    private fun session(mode: TrackingMode = TrackingMode.PERIODIC, owner: String? = "bob") =
        TrackingSession(owner, mode, if (mode == TrackingMode.LIVE) 901_000 else 0, 1_000, 7,
            RemoteTrackingState(500))

    @Test fun `periodic local and pause recover without converting pause into sharing`() {
        for (owner in listOf("bob", null)) for (mode in listOf(TrackingMode.PERIODIC, TrackingMode.STOPPED)) {
            val saved = session(mode, owner)
            assertEquals(Recovery.RESTORE, saved.recover(7, 2_000, true, true, owner))
            val policy = TrackingPolicy()
            saved.restore(policy, 2_000)
            assertEquals(mode, policy.mode(2_000))
        }
    }

    @Test fun `live resumes only the original monotonic remainder across repeated deaths`() {
        val saved = session(TrackingMode.LIVE)
        val policy = TrackingPolicy()
        saved.restore(policy, 601_000)
        assertEquals(300, policy.remainingSeconds(601_000))
        val again = saved.copy(savedElapsed = 601_000, liveUntilElapsed = policy.liveUntilElapsed)
        again.restore(policy, 701_000)
        assertEquals(200, policy.remainingSeconds(701_000))
        assertEquals(TrackingMode.PERIODIC, policy.mode(901_000))
        saved.restore(policy, 1_000_000)
        assertEquals(TrackingMode.PERIODIC, policy.mode(1_000_000))
    }

    @Test fun `unknown auth is different from signed out and wrong account`() {
        val saved = session()
        assertEquals(Recovery.WAIT_FOR_AUTH, saved.recover(7, 2_000, true, false, null))
        assertEquals(Recovery.REJECT, saved.recover(7, 2_000, true, true, null))
        assertEquals(Recovery.REJECT, saved.recover(7, 2_000, true, true, "alice"))
        assertEquals(Recovery.REJECT, session(owner = null).recover(7, 2_000, true, true, "bob"))
    }

    @Test fun `new boot elapsed rollback permissions and malformed state cannot recover`() {
        val saved = session()
        assertEquals(Recovery.REJECT, saved.recover(8, 2_000, true, true, "bob"))
        assertEquals(Recovery.REJECT, saved.recover(7, 999, true, true, "bob"))
        assertEquals(Recovery.REJECT, saved.recover(7, 2_000, false, true, "bob"))
        assertFalse(saved.copy(bootCount = -1).valid())
        assertFalse(saved.copy(ownerUid = "").valid())
        assertFalse(saved.copy(mode = TrackingMode.LIVE, liveUntilElapsed = Long.MAX_VALUE).valid())
        assertFalse(saved.copy(remote = RemoteTrackingState(2_000)).valid())
        assertFalse(saved.copy(pendingAck = TrackingRequest("alice", "bad", 1, TrackingMode.LIVE)).valid())
    }

    @Test fun `all consumed IDs ordering and local boundary survive recovery`() {
        val remote = RemoteTrackingPolicy(100)
        fun request(id: String, at: Long) = TrackingRequest("alice", id, at, TrackingMode.LIVE)
        val first = request("first", 1_000_000)
        val second = request("second", 1_000_001)
        assertNotNull(remote.accept(first, 1_000_000, 1_000))
        assertNotNull(remote.accept(second, 1_000_001, 1_001))
        remote.localChange(2_000)
        val restored = RemoteTrackingPolicy(remote.snapshot())
        assertNull(restored.accept(first, 1_000_002, 2_001))
        assertNull(restored.accept(second, 1_000_002, 2_001))
        assertNull(restored.accept(request("older-local", 1_000_002), 1_000_100, 2_010))
        assertNotNull(restored.accept(request("fresh", 1_000_101), 1_000_101, 2_011))
        // A rejected, already-seen command must also stay rejected after another process death.
        assertNull(RemoteTrackingPolicy(restored.snapshot()).accept(request("older-local", 1_000_102), 1_000_102, 2_012))
    }
    @Test fun `remote recovery requires exact current authorized command and never extends live`() {
        val command = TrackingRequest("alice", "first", 1_000_000, TrackingMode.LIVE)
        val saved = session(TrackingMode.LIVE).copy(activeRemote = command, pendingAck = command,
            remote = RemoteTrackingState(500, command.id, command.requestedAt, setOf(command.id)))
        assertTrue(saved.valid())
        val policy = TrackingPolicy()
        for (current in listOf(null, command.copy(id = "new"), command.copy(requesterUid = "eve"),
                command.copy(mode = TrackingMode.PERIODIC), command.copy(requestedAt = 1_000_001))) {
            assertFalse(saved.restoreRemote(policy, current, 601_000, 1_600_000))
            assertEquals(TrackingMode.STOPPED, policy.mode(601_000))
        }
        assertTrue(saved.restoreRemote(policy, command, 601_000, 1_600_000))
        assertEquals(300, policy.remainingSeconds(601_000))
        // A backwards wall clock cannot give back any elapsed minutes.
        assertTrue(saved.restoreRemote(policy, command, 701_000, 1_000_000))
        assertEquals(200, policy.remainingSeconds(701_000))
        assertTrue(saved.restoreRemote(policy, command, 901_000, 1_900_000))
        assertEquals(TrackingMode.PERIODIC, policy.mode(901_000))
    }

}
