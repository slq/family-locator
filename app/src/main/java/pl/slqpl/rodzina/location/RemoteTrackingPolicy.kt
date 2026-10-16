package pl.slqpl.rodzina.location

data class TrackingRequest(val requesterUid: String, val id: String, val requestedAt: Long, val mode: TrackingMode) {
    val expiresAt get() = requestedAt + TrackingPolicy.LIVE_DURATION
}

data class RemoteTrackingState(
    val boundaryElapsed: Long,
    val consumedId: String? = null,
    val newestTime: Long = Long.MIN_VALUE,
    val seenIds: Set<String> = emptySet(),
)

/** One server-ordered command per phone. Local actions supersede earlier remote commands. */
class RemoteTrackingPolicy(
    private var boundaryElapsed: Long,
    var consumedId: String? = null,
    private var newestTime: Long = Long.MIN_VALUE,
    seenIds: Set<String> = emptySet(),
) {
    constructor(state: RemoteTrackingState) : this(state.boundaryElapsed, state.consumedId, state.newestTime, state.seenIds)
    private val seen = linkedSetOf<String>().apply { addAll(seenIds); consumedId?.let(::add) }
    fun snapshot() = RemoteTrackingState(boundaryElapsed, consumedId, newestTime, seen.toSet())

    fun localChange(elapsedNow: Long) { boundaryElapsed = elapsedNow }

    fun accept(request: TrackingRequest?, serverNow: Long, elapsedNow: Long): TrackingRequest? {
        if (request == null || request.id in seen) return null
        // A fresh server offset may correct a temporarily future timestamp on reconnect.
        if (request.requestedAt > serverNow + 5_000) return null
        consumedId = request.id
        seen += request.id
        if (seen.size > 64) seen.remove(seen.first())
        // A service started locally must not replay a command sent while it was fully stopped.
        val beforeLocalAction = serverNow - request.requestedAt >= (elapsedNow - boundaryElapsed).coerceAtLeast(0)
        if (beforeLocalAction || request.expiresAt <= serverNow ||
            request.requestedAt < newestTime) return null
        newestTime = request.requestedAt
        return request
    }
}
