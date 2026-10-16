package pl.slqpl.rodzina.cloud

import org.junit.Assert.*
import org.junit.Test

class FamilyIconsTest {
    @Test fun `defaults are distinct and consistent across viewers and roster ordering`() {
        // Aa and BB intentionally have the same String hash.
        val uids = listOf("Aa", "BB", "alice", "bob", "carol", "dan", "eve", "fred", "gina", "henry", "ian", "jane")
        val first = FamilyIcons.resolve(uids, emptyMap())
        assertEquals(first, FamilyIcons.resolve(uids.reversed(), emptyMap()))
        assertEquals(uids.size, first.values.map { it.icon }.distinct().size)
        assertEquals(uids.size, first.values.map { it.number }.distinct().size)
        assertEquals(uids.size, first.values.map { it.colorArgb }.distinct().size)
    }

    @Test fun `explicit choices are preserved and automatic icons avoid them`() {
        val result = FamilyIcons.resolve(listOf("alice", "bob", "carol"), mapOf("bob" to "owl"))
        assertEquals(FamilyIcon.OWL, result.getValue("bob").icon)
        assertNotEquals(FamilyIcon.OWL, result.getValue("alice").icon)
        assertNotEquals(FamilyIcon.OWL, result.getValue("carol").icon)
    }

    @Test fun `repeated choices still get distinct markers and do not change member numbers`() {
        val uids = listOf("alice", "bob")
        val original = FamilyIcons.resolve(uids, emptyMap())
        val chosen = FamilyIcons.resolve(uids, mapOf("alice" to "cat", "bob" to "cat"))
        assertEquals(FamilyIcon.CAT, chosen.getValue("alice").icon)
        assertEquals(FamilyIcon.CAT, chosen.getValue("bob").icon)
        assertNotEquals(chosen.getValue("alice"), chosen.getValue("bob"))
        assertEquals(original.mapValues { it.value.number }, chosen.mapValues { it.value.number })
    }

    @Test fun `large rosters invalid selections and removed members keep usable distinct numbers`() {
        val uids = (1..30).map { "member-$it" }
        val result = FamilyIcons.resolve(uids, mapOf("member-1" to "invalid", "removed" to "owl"))
        assertEquals(FamilyIcons.resolve(uids, emptyMap()), result)
        assertEquals(30, result.values.map { it.number }.distinct().size)
        assertNull(FamilyIcon.fromId(null))
        assertNull(FamilyIcon.fromId("invalid"))
    }
}
