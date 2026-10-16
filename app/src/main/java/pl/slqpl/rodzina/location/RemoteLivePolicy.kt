package pl.slqpl.rodzina.location

data class RemoteLiveRequest(val requesterUid: String, val id: String, val requestedAt: Long) {
    val expiresAt: Long get() = requestedAt + TrackingPolicy.LIVE_DURATION
}

/** Each request gets one monotonic deadline. Reconnects never restart its fifteen minutes. */
class RemoteLivePolicy(val consumed: MutableMap<String, String> = mutableMapOf()) {
    private data class Session(val request: RemoteLiveRequest, val untilElapsed: Long)
    private val sessions = mutableMapOf<String, Session>()

    fun reconcile(requests: List<RemoteLiveRequest>, serverNow: Long, elapsedNow: Long): List<RemoteLiveRequest> {
        val current = requests.associateBy { it.requesterUid }
        sessions.entries.removeAll { (uid, session) ->
            current[uid]?.id != session.request.id || session.untilElapsed <= elapsedNow
        }
        val accepted = mutableListOf<RemoteLiveRequest>()
        for (request in requests) {
            if (consumed[request.requesterUid] == request.id) continue
            consumed[request.requesterUid] = request.id
            if (request.requestedAt > serverNow + 5_000 || request.expiresAt <= serverNow) continue
            val duration = (request.expiresAt - serverNow).coerceAtMost(TrackingPolicy.LIVE_DURATION)
            sessions[request.requesterUid] = Session(request, elapsedNow + duration)
            accepted += request
        }
        return accepted
    }

    fun deadline(elapsedNow: Long): Long = sessions.values.maxOfOrNull { it.untilElapsed }?.coerceAtLeast(elapsedNow) ?: 0
    fun active(elapsedNow: Long): List<RemoteLiveRequest> = sessions.values
        .filter { it.untilElapsed > elapsedNow }.map { it.request }
    fun cancel(): List<RemoteLiveRequest> = sessions.values.map { it.request }.also { sessions.clear() }
}
