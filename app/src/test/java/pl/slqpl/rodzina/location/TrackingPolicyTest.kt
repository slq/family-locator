package pl.slqpl.rodzina.location

import org.junit.Assert.*
import org.junit.Test

class TrackingPolicyTest {
    @Test fun `live expires at the deadline even without network`() {
        val policy = TrackingPolicy()
        policy.startLive(10_000)
        assertEquals(TrackingMode.LIVE, policy.mode(10_000 + TrackingPolicy.LIVE_DURATION - 1))
        assertEquals(TrackingMode.PERIODIC, policy.mode(10_000 + TrackingPolicy.LIVE_DURATION))
        assertEquals(0L, policy.remainingSeconds(10_000 + TrackingPolicy.LIVE_DURATION + 1))
    }
    @Test fun `a new policy never restores a previous live session`() {
        val previous = TrackingPolicy().apply { startLive(10) }
        assertEquals(TrackingMode.LIVE, previous.mode(20))
        assertEquals(TrackingMode.PERIODIC, TrackingPolicy().mode(20))
    }
    @Test fun `manual periodic mode cancels live`() {
        val policy = TrackingPolicy().apply { startLive(1_000) }
        policy.reset()
        assertEquals(TrackingMode.PERIODIC, policy.mode(2_000))
    }
    @Test fun `cached or invalid measurements are rejected`() {
        assertTrue(SamplePolicy.acceptable(52.0, 21.0, 10f, 1_000))
        assertFalse(SamplePolicy.acceptable(52.0, 21.0, 10f, 120_001))
        assertFalse(SamplePolicy.acceptable(52.0, 21.0, 10f, -1))
        assertFalse(SamplePolicy.acceptable(Double.NaN, 21.0, 10f, 0))
        assertFalse(SamplePolicy.acceptable(52.0, 181.0, 10f, 0))
        assertFalse(SamplePolicy.acceptable(52.0, 21.0, Float.NaN, 0))
        assertFalse(SamplePolicy.acceptable(52.0, 21.0, 0f, 0))
    }
    @Test fun `off cancels all live deadlines and stays off past their expiry`() {
        val policy = TrackingPolicy().apply { startLive(1_000) }
        policy.setMode(TrackingMode.STOPPED, 2_000)
        assertEquals(TrackingMode.STOPPED, policy.mode(2_000))
        assertEquals(TrackingMode.STOPPED, policy.mode(2_000_000))
        assertEquals(0L, policy.remainingSeconds(2_000))
        policy.setMode(TrackingMode.PERIODIC, 3_000)
        assertEquals(TrackingMode.PERIODIC, policy.mode(3_000))
    }
}
