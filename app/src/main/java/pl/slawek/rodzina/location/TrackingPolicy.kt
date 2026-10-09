package pl.slawek.rodzina.location

enum class TrackingMode(val intervalMillis: Long, val label: String) {
    PERIODIC(180_000, "Okresowy — około 3 min"),
    LIVE(10_000, "Dokładny — około 10 s"),
}

/** A monotonic deadline prevents wall-clock changes from extending a live session. */
class TrackingPolicy {
    private var liveUntilElapsed = 0L

    fun startLive(elapsedMillis: Long) { liveUntilElapsed = elapsedMillis + LIVE_DURATION }
    fun reset() { liveUntilElapsed = 0 }
    fun mode(elapsedMillis: Long): TrackingMode =
        if (elapsedMillis < liveUntilElapsed) TrackingMode.LIVE else TrackingMode.PERIODIC
    fun remainingSeconds(elapsedMillis: Long): Long =
        ((liveUntilElapsed - elapsedMillis).coerceAtLeast(0) + 999) / 1000

    companion object { const val LIVE_DURATION = 15 * 60 * 1000L }
}

object SamplePolicy {
    const val RETENTION_MILLIS = 24 * 60 * 60 * 1000L
    const val MAX_SAMPLES = 1_000
    fun acceptable(latitude: Double, longitude: Double, accuracy: Float, ageMillis: Long): Boolean =
        latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0 &&
            accuracy.isFinite() && accuracy > 0 && ageMillis in 0..120_000
}
