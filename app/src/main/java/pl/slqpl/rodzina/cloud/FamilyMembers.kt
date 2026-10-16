package pl.slqpl.rodzina.cloud

import pl.slqpl.rodzina.location.RemoteLiveRequest

data class FamilyDevice(val sharing: Boolean, val mode: String, val updatedAt: Long, val liveUntil: Long) {
    fun available(now: Long) = sharing && updatedAt in (now - 90_000)..(now + 10_000)
}
data class LiveAcknowledgment(val requestId: String, val state: String, val expiresAt: Long, val respondedAt: Long)
enum class LiveRequestState { NONE, PENDING, ACTIVE, STOPPED, EXPIRED }
data class FamilyMember(
    val uid: String,
    val name: String,
    val position: FamilyPosition?,
    val device: FamilyDevice?,
    val request: RemoteLiveRequest?,
    val acknowledgment: LiveAcknowledgment?,
    val appearance: MemberAppearance = MemberAppearance(),
    val selectedIcon: FamilyIcon? = null,
) {
    fun requestState(now: Long): LiveRequestState {
        val command = request ?: return LiveRequestState.NONE
        if (command.expiresAt <= now) return LiveRequestState.EXPIRED
        val ack = acknowledgment?.takeIf { it.requestId == command.id } ?: return LiveRequestState.PENDING
        if (ack.state == "stopped") return LiveRequestState.STOPPED
        if (ack.state == "active" && device != null && device.updatedAt in (now - 90_000)..(now + 10_000) &&
            device.updatedAt >= ack.respondedAt && device.sharing && device.mode == "periodic") return LiveRequestState.STOPPED
        return if (ack.state == "active" && ack.expiresAt > now && device?.available(now) == true &&
            device.mode == "live" && device.liveUntil > now) LiveRequestState.ACTIVE else LiveRequestState.PENDING
    }
}
data class FamilySnapshot(val members: List<FamilyMember> = emptyList(), val serverTimeOffset: Long = 0) {
    val positions get() = members.mapNotNull { it.position }
}
data class RemoteControl(val requests: List<RemoteLiveRequest>, val serverTimeOffset: Long, val connected: Boolean)
