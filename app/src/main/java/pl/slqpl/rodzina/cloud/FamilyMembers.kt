package pl.slqpl.rodzina.cloud

import pl.slqpl.rodzina.location.TrackingRequest
import pl.slqpl.rodzina.location.TrackingMode

data class FamilyDevice(val sharing: Boolean, val mode: String, val updatedAt: Long, val liveUntil: Long,
    val controlReady: Boolean = false) {
    fun fresh(now: Long) = updatedAt in (now - 90_000)..(now + 10_000)
    fun available(now: Long) = sharing && updatedAt in (now - 90_000)..(now + 10_000)
    fun controllable(now: Long) = controlReady && fresh(now)
}
data class TrackingAcknowledgment(val requestId: String, val mode: String, val respondedAt: Long)
enum class TrackingRequestState { NONE, PENDING, CONFIRMED, CHANGED, FINISHED, EXPIRED, UNAVAILABLE }
data class FamilyMember(
    val uid: String,
    val name: String,
    val position: FamilyPosition?,
    val device: FamilyDevice?,
    val request: TrackingRequest?,
    val acknowledgment: TrackingAcknowledgment?,
    val appearance: MemberAppearance = MemberAppearance(),
    val selectedIcon: FamilyIcon? = null,
    val historyDeletedThrough: Long = 0,
) {
    fun requestState(now: Long): TrackingRequestState {
        val command = request ?: return TrackingRequestState.NONE
        val ack = acknowledgment?.takeIf { it.requestId == command.id && it.mode == command.mode.wireName }
            ?: return if (command.expiresAt <= now) TrackingRequestState.EXPIRED else TrackingRequestState.PENDING
        if (command.mode == TrackingMode.LIVE && command.expiresAt <= now) return TrackingRequestState.FINISHED
        if (device?.controllable(now) != true || device.updatedAt < ack.respondedAt) return TrackingRequestState.UNAVAILABLE
        return if (device.mode == command.mode.wireName && device.sharing == (command.mode != TrackingMode.STOPPED) &&
            (command.mode != TrackingMode.LIVE || device.liveUntil > now)) TrackingRequestState.CONFIRMED else TrackingRequestState.CHANGED
    }
}
data class FamilySnapshot(val members: List<FamilyMember> = emptyList(), val serverTimeOffset: Long = 0) {
    val positions get() = members.mapNotNull { it.position }
}
data class RemoteControl(val request: TrackingRequest?, val serverTimeOffset: Long, val connected: Boolean)
