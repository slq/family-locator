package pl.slqpl.rodzina.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.slqpl.rodzina.cloud.FamilyDevice
import pl.slqpl.rodzina.cloud.FamilyMember
import pl.slqpl.rodzina.cloud.FamilyPosition

class MemberPresentationTest {
    private val now = 1_800_000_000_000L
    private fun member(age: Long = 1_000, device: FamilyDevice? = FamilyDevice(true, "periodic", now, 0)) =
        FamilyMember("anna", "Anna", FamilyPosition("anna", "Anna", 52.0, 21.0, 12f, now - age, now, 82), device, null, null)

    @Test fun oldMeasurementIsMarkedStaleEvenWhenThePhoneIsConnected() {
        assertEquals(MemberStatus.STALE, memberStatus(member(age = 42 * 60_000), now))
    }
    @Test fun recentMeasurementDoesNotImplyThatThePhoneIsAvailable() {
        assertEquals(MemberStatus.UNCONFIRMED, memberStatus(member(device = null), now))
        assertEquals(MemberStatus.UNCONFIRMED, memberStatus(member(device = FamilyDevice(true, "periodic", now - 100_000, 0)), now))
    }
    @Test fun freshSharingAndExplicitPauseHaveDistinctLabels() {
        assertEquals(MemberStatus.SHARING, memberStatus(member(), now))
        assertEquals(MemberStatus.PAUSED, memberStatus(member(device = FamilyDevice(false, "stopped", now, 0)), now))
    }
    @Test fun futureMeasurementReportsClockProblem() {
        assertEquals(MemberStatus.CLOCK, memberStatus(member(age = -60_000), now))
        assertEquals("sprawdź zegar telefonu", measurementAge(now + 60_000, now))
    }
    @Test fun missingPositionIsStillAValidFamilyMember() {
        assertEquals(MemberStatus.NO_POSITION, memberStatus(member().copy(position = null), now))
    }
    @Test fun ageFormattingKeepsClockToleranceAndLongOfflinePeriodsReadable() {
        assertEquals("przed chwilą", measurementAge(now + 5_000, now))
        assertEquals("42 min temu", measurementAge(now - 42 * 60_000, now))
        assertEquals("2 godz. temu", measurementAge(now - 2 * 3_600_000, now))
        assertEquals("2 dni temu", measurementAge(now - 2 * 86_400_000, now))
    }
}
