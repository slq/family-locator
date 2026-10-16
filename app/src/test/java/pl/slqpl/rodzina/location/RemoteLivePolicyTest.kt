package pl.slqpl.rodzina.location

import org.junit.Assert.*
import org.junit.Test

class RemoteLivePolicyTest {
    private val serverNow = 1_000_000L
    private fun request(uid: String = "alice", id: String = "first", at: Long = serverNow) = RemoteLiveRequest(uid, id, at)

    @Test fun `delayed delivery uses only the remaining part of fifteen minutes`() {
        val policy = RemoteLivePolicy()
        val command = request(at = serverNow - 300_000)
        assertEquals(listOf(command), policy.reconcile(listOf(command), serverNow, 10_000))
        assertEquals(610_000L, policy.deadline(10_000))
        assertTrue(policy.active(610_000).isEmpty())
    }

    @Test fun `expired or future requests cannot start tracking`() {
        val policy = RemoteLivePolicy()
        assertTrue(policy.reconcile(listOf(request(at = serverNow - TrackingPolicy.LIVE_DURATION)), serverNow, 10).isEmpty())
        assertTrue(policy.reconcile(listOf(request(id = "future", at = serverNow + 6_000)), serverNow, 10).isEmpty())
        assertEquals(0L, policy.deadline(10))
    }

    @Test fun `duplicates reconnects and a backwards wall clock never extend the monotonic deadline`() {
        val policy = RemoteLivePolicy()
        val command = request()
        policy.reconcile(listOf(command), serverNow, 1_000)
        val deadline = policy.deadline(1_000)
        assertTrue(policy.reconcile(listOf(command), serverNow - 60_000, 61_000).isEmpty())
        assertEquals(deadline, policy.deadline(61_000))
    }

    @Test fun `manual cancellation and service recreation do not replay an old request`() {
        val policy = RemoteLivePolicy()
        val command = request()
        policy.reconcile(listOf(command), serverNow, 1_000)
        assertEquals(listOf(command), policy.cancel())
        assertTrue(policy.reconcile(listOf(command), serverNow + 1, 1_001).isEmpty())
        val recreated = RemoteLivePolicy(policy.consumed.toMutableMap())
        assertTrue(recreated.reconcile(listOf(command), serverNow + 1, 1_001).isEmpty())
        assertEquals(0L, recreated.deadline(1_001))
        assertEquals(1, recreated.reconcile(listOf(request(id = "new")), serverNow + 1, 1_001).size)
    }

    @Test fun `multiple requesters have independent deadlines and revoked requesters stop`() {
        val policy = RemoteLivePolicy()
        val early = request(at = serverNow - 300_000)
        val late = request(uid = "bob")
        policy.reconcile(listOf(early, late), serverNow, 10_000)
        assertEquals(910_000L, policy.deadline(10_000))
        policy.reconcile(listOf(early), serverNow + 1, 10_001)
        assertEquals(610_000L, policy.deadline(10_001))
        policy.reconcile(emptyList(), serverNow + 2, 10_002)
        assertEquals(0L, policy.deadline(10_002))
    }

    @Test fun `remote cancellation never shortens a local live session`() {
        val policy = TrackingPolicy()
        policy.startLive(1_000)
        policy.setRemoteDeadline(1_500_000)
        assertEquals(TrackingMode.LIVE, policy.mode(1_000_000))
        policy.setRemoteDeadline(0)
        assertEquals(TrackingMode.LIVE, policy.mode(2_000))
        assertEquals(TrackingMode.PERIODIC, policy.mode(901_000))
        policy.setRemoteDeadline(1_500_000)
        policy.reset()
        assertEquals(TrackingMode.PERIODIC, policy.mode(2_000))
    }
}
