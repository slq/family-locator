package pl.slqpl.rodzina.ui

import pl.slqpl.rodzina.cloud.FamilyMember

enum class MemberStatus(val label: String, val attention: Boolean = false) {
    SHARING("Udostępnia"), PAUSED("Wyłączone"), STALE("Stara pozycja", true),
    NO_POSITION("Brak pozycji"), UNCONFIRMED("Brak potwierdzenia", true), CLOCK("Sprawdź zegar", true),
}

fun memberStatus(member: FamilyMember, now: Long): MemberStatus = when {
    member.position?.let { it.measuredAt > now + 10_000 } == true -> MemberStatus.CLOCK
    member.device?.fresh(now) == true && !member.device.sharing -> MemberStatus.PAUSED
    member.position == null -> MemberStatus.NO_POSITION
    now - member.position.measuredAt > 5 * 60_000 -> MemberStatus.STALE
    member.device?.available(now) == true -> MemberStatus.SHARING
    else -> MemberStatus.UNCONFIRMED
}

fun measurementAge(measuredAt: Long, now: Long): String {
    val seconds = (now - measuredAt) / 1_000
    return when {
        measuredAt > now + 10_000 -> "sprawdź zegar telefonu"
        seconds < 60 -> "przed chwilą"
        seconds < 3_600 -> "${seconds / 60} min temu"
        seconds < 86_400 -> "${seconds / 3_600} godz. temu"
        else -> "${seconds / 86_400} dni temu"
    }
}
