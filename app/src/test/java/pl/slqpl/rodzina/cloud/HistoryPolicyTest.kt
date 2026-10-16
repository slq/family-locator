package pl.slqpl.rodzina.cloud

import org.junit.Assert.*
import org.junit.Test
import pl.slqpl.rodzina.data.LocationSample
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HistoryPolicyTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val now = Instant.parse("2026-10-16T12:00:00Z").toEpochMilli()
    private fun sample(time: Long) = LocationSample(ownerUid = "alice", latitude = 52.0, longitude = 21.0,
        accuracyMeters = 10f, measuredAt = time, batteryPercent = 70)

    @Test fun `local calendar days include both endpoints across daylight saving changes`() {
        for ((date, hours) in listOf("2026-03-29" to 23, "2026-10-25" to 25)) {
            val day = LocalDate.parse(date)
            val futureNow = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val range = HistoryPolicy.range(day, futureNow, 0, zone)
            assertEquals(hours * 3_600_000L, range.endInclusive - range.start + 1)
            assertEquals(day, HistoryPolicy.day(range.start, zone))
            assertEquals(day, HistoryPolicy.day(range.endInclusive, zone))
            assertEquals(day.plusDays(1), HistoryPolicy.day(range.endInclusive + 1, zone))
        }
    }

    @Test fun `the first retained day is partial and deleted points are excluded inclusively`() {
        val first = now - HistoryPolicy.RETENTION_MILLIS
        val day = HistoryPolicy.day(first, zone)
        assertEquals(first, HistoryPolicy.range(day, now, 0, zone).start)
        assertEquals(first + 51, HistoryPolicy.range(day, now, first + 50, zone).start)
        val end = HistoryPolicy.range(day, now, 0, zone).endInclusive
        assertTrue(HistoryPolicy.range(day, now, end, zone).empty)
    }

    @Test fun `deleting yesterday includes its last millisecond and deleting today stops at now`() {
        val today = HistoryPolicy.day(now, zone)
        val midnight = today.atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(midnight - 1, HistoryPolicy.deletionCutoff(today.minusDays(1), now, zone))
        assertEquals(now, HistoryPolicy.deletionCutoff(today, now, zone))
        assertThrows(IllegalArgumentException::class.java) { HistoryPolicy.deletionCutoff(today.plusDays(1), now, zone) }
    }

    @Test fun `offline points may be archived for thirty days but cannot replace a fresh live position`() {
        val old = sample(now - 10 * 86_400_000L)
        assertTrue(HistoryPolicy.acceptable(old, now, 0))
        assertFalse(HistoryPolicy.current(old, now, 0))
        assertTrue(HistoryPolicy.current(sample(now - 1000), now, 0))
        val oldest = sample(now - HistoryPolicy.RETENTION_MILLIS)
        assertTrue(HistoryPolicy.acceptable(oldest, now, 0))
        assertFalse(HistoryPolicy.acceptable(oldest.copy(measuredAt = oldest.measuredAt - 1), now, 0))
        assertFalse(HistoryPolicy.acceptable(sample(now), now, now))
        assertTrue(HistoryPolicy.acceptable(sample(now + 1), now, now))
        assertFalse(HistoryPolicy.acceptable(sample(now + 60_001), now, 0))
    }

    @Test fun `invalid samples never enter the route`() {
        val valid = sample(now)
        for (invalid in listOf(valid.copy(latitude = Double.NaN), valid.copy(longitude = 181.0),
            valid.copy(accuracyMeters = 0f), valid.copy(accuracyMeters = Float.POSITIVE_INFINITY), valid.copy(batteryPercent = 101))) {
            assertFalse(HistoryPolicy.acceptable(invalid, now, 0))
        }
    }

    @Test fun `route sorts points and never joins a gap longer than fifteen minutes`() {
        fun point(id: String, time: Long) = HistoryPoint(id, 52.0, 21.0, 10f, time, 70)
        val first = point("first", now)
        val second = point("second", now + HistoryPolicy.MAX_GAP_MILLIS)
        val third = point("third", second.measuredAt + HistoryPolicy.MAX_GAP_MILLIS + 1)
        assertEquals(listOf(listOf(first, second), listOf(third)), HistoryPolicy.segments(listOf(third, second, first)))
        assertTrue(HistoryPolicy.segments(emptyList()).isEmpty())
    }
}
