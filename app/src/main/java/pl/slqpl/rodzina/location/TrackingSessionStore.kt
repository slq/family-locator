package pl.slqpl.rodzina.location

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A synchronous, single-value commit precedes every observable mode change. */
class TrackingSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("tracking_session", Context.MODE_PRIVATE)

    fun read(): TrackingSession? = runCatching {
        val json = JSONObject(preferences.getString("session", null) ?: return null)
        require(json.getInt("version") == 1 && json.getBoolean("enabled"))
        val local = json.getBoolean("local")
        val uid = if (json.isNull("uid")) null else json.getString("uid")
        require(local == (uid == null))
        val seen = json.getJSONArray("seen")
        TrackingSession(uid, TrackingMode.valueOf(json.getString("mode")), json.getLong("liveUntil"),
            json.getLong("elapsed"), json.getInt("boot"),
            RemoteTrackingState(json.getLong("boundary"), json.nullableString("consumed"),
                json.getLong("newest"), (0 until seen.length()).map { seen.getString(it) }.toSet()),
            json.request("active"), json.request("ack")).also { require(it.valid()) }
    }.getOrNull()

    fun write(session: TrackingSession): Boolean {
        if (!session.valid() || preferences.getBoolean("disabled", false)) return false
        val json = JSONObject().put("version", 1).put("enabled", true)
            .put("local", session.ownerUid == null).put("uid", session.ownerUid ?: JSONObject.NULL)
            .put("mode", session.mode.name).put("liveUntil", session.liveUntilElapsed)
            .put("elapsed", session.savedElapsed).put("boot", session.bootCount)
            .put("boundary", session.remote.boundaryElapsed)
            .put("consumed", session.remote.consumedId ?: JSONObject.NULL)
            .put("newest", session.remote.newestTime).put("seen", JSONArray(session.remote.seenIds.toList()))
            .put("active", session.activeRemote.json()).put("ack", session.pendingAck.json())
        return preferences.edit().clear().putString("session", json.toString()).commit()
    }

    fun beginLocalStart(): Boolean = preferences.edit().clear().commit()

    fun disable(): Boolean = preferences.edit().clear().putBoolean("disabled", true).commit()

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else getString(key)
    private fun JSONObject.request(key: String): TrackingRequest? {
        if (isNull(key)) return null
        val value = getJSONObject(key)
        return TrackingRequest(value.getString("uid"), value.getString("id"), value.getLong("at"),
            TrackingMode.valueOf(value.getString("mode")))
    }
    private fun TrackingRequest?.json(): Any = this?.let {
        JSONObject().put("uid", requesterUid).put("id", id).put("at", requestedAt).put("mode", mode.name)
    } ?: JSONObject.NULL
}
