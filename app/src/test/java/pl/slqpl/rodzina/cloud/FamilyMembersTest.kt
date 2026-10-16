package pl.slqpl.rodzina.cloud

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.slqpl.rodzina.location.RemoteLiveRequest

class FamilyMembersTest {
    private val now = 1_000_000L
    private val command = RemoteLiveRequest("alice", "id", now - 1_000)
    private val live = FamilyDevice(true, "live", now, command.expiresAt)
    private val ack = LiveAcknowledgment("id", "active", command.expiresAt, now)
    private fun member(device: FamilyDevice? = live, acknowledgment: LiveAcknowledgment? = ack) =
        FamilyMember("bob", "Bob", null, device, command, acknowledgment)

    @Test fun `request is pending until the target confirms this request and an active device`() {
        assertEquals(LiveRequestState.PENDING, member(acknowledgment = null).requestState(now))
        assertEquals(LiveRequestState.PENDING, member(acknowledgment = ack.copy(requestId = "previous")).requestState(now))
        assertEquals(LiveRequestState.PENDING, member(device = null).requestState(now))
        assertEquals(LiveRequestState.PENDING, member(device = live.copy(mode = "periodic", liveUntil = 0, updatedAt = now - 1)).requestState(now))
        assertEquals(LiveRequestState.ACTIVE, member().requestState(now))
    }
    @Test fun `stale or disconnected devices cannot claim active tracking`() {
        assertEquals(LiveRequestState.PENDING, member(device = live.copy(updatedAt = now - 90_001)).requestState(now))
        assertEquals(LiveRequestState.PENDING, member(device = live.copy(sharing = false, mode = "stopped", liveUntil = 0)).requestState(now))
        assertEquals(LiveRequestState.STOPPED, member(device = live.copy(mode = "periodic", liveUntil = 0)).requestState(now))
    }
    @Test fun `stop and expiry are displayed separately`() {
        assertEquals(LiveRequestState.STOPPED, member(acknowledgment = ack.copy(state = "stopped")).requestState(now))
        assertEquals(LiveRequestState.EXPIRED, member().requestState(command.expiresAt))
        assertEquals(LiveRequestState.NONE, member().copy(request = null).requestState(now))
    }
}
