package pl.slqpl.rodzina.location

/** Everything needed to resume on the same boot, without trusting the wall clock. */
data class TrackingSession(
    val ownerUid: String?,
    val mode: TrackingMode,
    val liveUntilElapsed: Long,
    val savedElapsed: Long,
    val bootCount: Int,
    val remote: RemoteTrackingState,
    val activeRemote: TrackingRequest? = null,
    val pendingAck: TrackingRequest? = null,
) {
    fun valid(): Boolean = bootCount >= 0 && savedElapsed >= 0 &&
        (ownerUid == null || ownerUid.isNotBlank()) && remote.boundaryElapsed in 0..savedElapsed &&
        remote.seenIds.size <= 64 && remote.seenIds.all { it.isNotBlank() } &&
        (remote.consumedId == null || remote.consumedId in remote.seenIds) &&
        (mode != TrackingMode.LIVE || liveUntilElapsed > savedElapsed &&
            liveUntilElapsed - savedElapsed <= TrackingPolicy.LIVE_DURATION) &&
        (mode == TrackingMode.LIVE || liveUntilElapsed == 0L) &&
        (activeRemote == null || ownerUid != null && activeRemote.id in remote.seenIds &&
            activeRemote.requesterUid.isNotBlank() && activeRemote.requestedAt > 0 &&
            activeRemote.requestedAt <= Long.MAX_VALUE - TrackingPolicy.LIVE_DURATION) &&
        (pendingAck == null || pendingAck == activeRemote)

    fun recover(boot: Int, elapsedNow: Long, permissions: Boolean, authReady: Boolean, uid: String?): Recovery {
        if (!valid() || boot != bootCount || elapsedNow < savedElapsed || !permissions) return Recovery.REJECT
        if (!authReady) return Recovery.WAIT_FOR_AUTH
        return if (uid == ownerUid) Recovery.RESTORE else Recovery.REJECT
    }

    /** Caller must obtain current from an authenticated server read, including the family roster. */
    fun restoreRemote(policy: TrackingPolicy, current: TrackingRequest?, elapsedNow: Long, serverNow: Long): Boolean {
        if (activeRemote == null || current != activeRemote) {
            policy.setMode(TrackingMode.STOPPED, elapsedNow)
            return false
        }
        restore(policy, elapsedNow)
        if (policy.mode(elapsedNow) == TrackingMode.LIVE) {
            policy.setMode(TrackingMode.LIVE, elapsedNow,
                minOf(liveUntilElapsed - elapsedNow, current!!.expiresAt - serverNow))
        }
        return true
    }

    fun restore(policy: TrackingPolicy, elapsedNow: Long) {
        policy.setMode(mode, elapsedNow, (liveUntilElapsed - elapsedNow).coerceAtLeast(0))
    }
}

enum class Recovery { WAIT_FOR_AUTH, RESTORE, REJECT }
