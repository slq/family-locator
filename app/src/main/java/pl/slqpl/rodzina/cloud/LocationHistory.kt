package pl.slqpl.rodzina.cloud

import pl.slqpl.rodzina.data.LocationSample
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HistoryPoint(val id: String, val latitude: Double, val longitude: Double,
    val accuracyMeters: Float, val measuredAt: Long, val batteryPercent: Int)

data class HistorySelection(val uid: String, val day: LocalDate)

data class HistorySnapshot(
    val selection: HistorySelection? = null,
    val points: List<HistoryPoint> = emptyList(),
    val deletedThrough: Long = 0,
    val loading: Boolean = false,
    val error: String = "",
)

data class HistoryRange(val start: Long, val endInclusive: Long) { val empty get() = start > endInclusive }

object HistoryPolicy {
    const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    const val MAX_DAY_POINTS = 10_000
    const val BATCH_SIZE = 200
    const val CLEANUP_PAGE_SIZE = 1_000
    const val MAX_GAP_MILLIS = 15 * 60 * 1000L

    fun day(now: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    fun range(date: LocalDate, now: Long, deletedThrough: Long, zone: ZoneId = ZoneId.systemDefault()): HistoryRange =
        HistoryRange(maxOf(date.atStartOfDay(zone).toInstant().toEpochMilli(), now - RETENTION_MILLIS, deletedThrough + 1),
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)

    fun deletionCutoff(date: LocalDate, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        require(date <= day(now, zone)) { "Nie można wybrać przyszłej daty" }
        return minOf(now, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
    }

    fun acceptable(sample: LocationSample, now: Long, deletedThrough: Long): Boolean =
        sample.measuredAt > deletedThrough && sample.measuredAt >= now - RETENTION_MILLIS && sample.measuredAt <= now + 60_000 &&
            sample.latitude.isFinite() && sample.latitude in -90.0..90.0 &&
            sample.longitude.isFinite() && sample.longitude in -180.0..180.0 &&
            sample.accuracyMeters.isFinite() && sample.accuracyMeters > 0 && sample.accuracyMeters <= 100_000 &&
            sample.batteryPercent in -1..100

    fun current(sample: LocationSample, now: Long, deletedThrough: Long): Boolean =
        acceptable(sample, now, deletedThrough) && sample.measuredAt >= now - 24 * 60 * 60 * 1000L

    /** Never draw a continuous route across a long gap in measurements. */
    fun segments(points: List<HistoryPoint>): List<List<HistoryPoint>> {
        val result = mutableListOf<MutableList<HistoryPoint>>()
        points.sortedWith(compareBy<HistoryPoint> { it.measuredAt }.thenBy { it.id }).forEach { point ->
            if (result.isEmpty() || point.measuredAt - result.last().last().measuredAt > MAX_GAP_MILLIS) result += mutableListOf(point)
            else result.last() += point
        }
        return result
    }
}
