package pl.slqpl.rodzina.cloud

import org.junit.Assert.*
import org.junit.Test
import pl.slqpl.rodzina.location.TrackingRequest
import pl.slqpl.rodzina.location.TrackingMode

class FamilyMembersTest {
    private val now = 1_000_000L
    private val command = TrackingRequest("alice", "id", now - 1_000, TrackingMode.LIVE)
    private val live = FamilyDevice(true, "live", now, command.expiresAt, true)
    private val ack = TrackingAcknowledgment("id", "live", now)
    private fun member(device: FamilyDevice? = live, acknowledgment: TrackingAcknowledgment? = ack) =
        FamilyMember("bob", "Bob", null, device, command, acknowledgment)

    @Test fun `a command waits for matching confirmation and fresh control status`() {
        assertEquals(TrackingRequestState.PENDING, member(acknowledgment = null).requestState(now))
        assertEquals(TrackingRequestState.PENDING, member(acknowledgment = ack.copy(requestId = "previous")).requestState(now))
        assertEquals(TrackingRequestState.PENDING, member(acknowledgment = ack.copy(mode = "stopped")).requestState(now))
        assertEquals(TrackingRequestState.UNAVAILABLE, member(device = null).requestState(now))
        assertEquals(TrackingRequestState.UNAVAILABLE, member(device = live.copy(updatedAt = now - 90_001)).requestState(now))
        assertEquals(TrackingRequestState.CONFIRMED, member().requestState(now))
    }

    @Test fun `stopped sharing can be confirmed while control stays available`() {
        val stopped = member(device = FamilyDevice(false, "stopped", now, 0, true),
            acknowledgment = ack.copy(mode = "stopped")).copy(request = command.copy(mode = TrackingMode.STOPPED))
        assertEquals(TrackingRequestState.CONFIRMED, stopped.requestState(now))
        assertFalse(stopped.device!!.available(now))
        assertTrue(stopped.device.controllable(now))
        assertEquals(TrackingRequestState.UNAVAILABLE, stopped.copy(device = stopped.device.copy(controlReady = false)).requestState(now))
        val later = stopped.copy(device = stopped.device.copy(updatedAt = now + 1_000_000))
        assertEquals(TrackingRequestState.CONFIRMED, later.requestState(now + 1_000_000))
    }

    @Test fun `local overrides expiry and a completed live session have distinct states`() {
        assertEquals(TrackingRequestState.CHANGED, member(device = live.copy(mode = "periodic", liveUntil = 0)).requestState(now))
        assertEquals(TrackingRequestState.FINISHED, member().requestState(command.expiresAt))
        assertEquals(TrackingRequestState.EXPIRED, member(acknowledgment = null).requestState(command.expiresAt))
        assertEquals(TrackingRequestState.NONE, member().copy(request = null).requestState(now))
    }
}
