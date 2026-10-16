package pl.slqpl.rodzina.location

enum class TrackingMode(val intervalMillis: Long, val label: String) {
    STOPPED(0, "Udostępnianie wyłączone"),
    PERIODIC(180_000, "Okresowy — około 3 min"),
    LIVE(10_000, "Dokładny — około 10 s");

    val wireName get() = name.lowercase()
    companion object { fun fromWire(value: String) = entries.firstOrNull { it.wireName == value } }
}

/** A monotonic deadline prevents wall-clock changes from extending a live session. */
class TrackingPolicy {
    var liveUntilElapsed = 0L
        private set
    private var sharing = true

    fun setMode(mode: TrackingMode, elapsedMillis: Long, liveDuration: Long = LIVE_DURATION) {
        sharing = mode != TrackingMode.STOPPED
        liveUntilElapsed = if (mode == TrackingMode.LIVE) elapsedMillis + liveDuration.coerceIn(0, LIVE_DURATION) else 0
    }
    fun startLive(elapsedMillis: Long) = setMode(TrackingMode.LIVE, elapsedMillis)
    fun reset() = setMode(TrackingMode.PERIODIC, 0)
    fun mode(elapsedMillis: Long): TrackingMode =
        if (!sharing) TrackingMode.STOPPED else if (elapsedMillis < liveUntilElapsed) TrackingMode.LIVE else TrackingMode.PERIODIC
    fun remainingSeconds(elapsedMillis: Long): Long =
        ((liveUntilElapsed - elapsedMillis).coerceAtLeast(0) + 999) / 1000

    companion object { const val LIVE_DURATION = 15 * 60 * 1000L }
}

object SamplePolicy {
    const val RETENTION_MILLIS = 30 * 24 * 60 * 60 * 1000L
    const val MAX_SAMPLES = 300_000
    fun acceptable(latitude: Double, longitude: Double, accuracy: Float, ageMillis: Long): Boolean =
        latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0 &&
            accuracy.isFinite() && accuracy > 0 && ageMillis in 0..120_000
}
