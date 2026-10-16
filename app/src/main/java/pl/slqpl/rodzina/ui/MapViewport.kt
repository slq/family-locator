package pl.slqpl.rodzina.ui

import pl.slqpl.rodzina.cloud.FamilySnapshot
import pl.slqpl.rodzina.data.LocationSample

data class MapPoint(val uid: String, val latitude: Double, val longitude: Double)

data class FamilyMapView(val memberUid: String? = null, val following: Boolean = true, val requestId: Int = 0) {
    fun focus(uid: String) = copy(memberUid = uid, following = true, requestId = requestId + 1)
    fun showAll() = copy(memberUid = null, following = true, requestId = requestId + 1)
    fun pause() = copy(following = false)
    fun resume() = copy(following = true, requestId = requestId + 1)
    fun reconcileMembers(uids: Set<String>) =
        if (memberUid != null && uids.isNotEmpty() && memberUid !in uids) showAll() else this

    fun targets(points: List<MapPoint>): List<MapPoint> = points
        .filter { memberUid == null || it.uid == memberUid }
        .distinctBy { it.latitude to it.longitude }
}

/** Family coordinates take precedence over an old local cache, which must not widen family bounds. */
fun mapPoints(snapshot: FamilySnapshot, local: LocationSample?, currentUid: String?): List<MapPoint> {
    val family = snapshot.positions.map { MapPoint(it.uid, it.latitude, it.longitude) }
    val deletedThrough = snapshot.members.firstOrNull { it.uid == currentUid }?.historyDeletedThrough ?: 0
    val points = if (family.isNotEmpty()) family else local?.takeIf { it.ownerUid == currentUid && it.measuredAt > deletedThrough }?.let {
        listOf(MapPoint(currentUid ?: "local", it.latitude, it.longitude))
    }.orEmpty()
    return points.filter { it.latitude.isFinite() && it.latitude in -90.0..90.0 &&
        it.longitude.isFinite() && it.longitude in -180.0..180.0 }.sortedBy { it.uid }
}
