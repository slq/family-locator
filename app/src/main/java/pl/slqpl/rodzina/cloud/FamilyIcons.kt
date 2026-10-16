package pl.slqpl.rodzina.cloud

enum class FamilyIcon(val id: String, val glyph: String, val label: String) {
    CAT("cat", "🐱", "Kot"),
    DOG("dog", "🐶", "Pies"),
    FOX("fox", "🦊", "Lis"),
    BEAR("bear", "🐻", "Miś"),
    PANDA("panda", "🐼", "Panda"),
    RABBIT("rabbit", "🐰", "Królik"),
    KOALA("koala", "🐨", "Koala"),
    TIGER("tiger", "🐯", "Tygrys"),
    LION("lion", "🦁", "Lew"),
    OWL("owl", "🦉", "Sowa"),
    FROG("frog", "🐸", "Żaba"),
    PENGUIN("penguin", "🐧", "Pingwin");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id }
    }
}

data class MemberAppearance(val icon: FamilyIcon = FamilyIcon.CAT, val number: Int = 1) {
    val colorArgb: Int get() = colors[(number - 1).coerceAtLeast(0) % colors.size]

    companion object {
        private val colors = intArrayOf(
            0xFF176B5B.toInt(), 0xFF285EC4.toInt(), 0xFF9A3DB4.toInt(), 0xFFBE4A16.toInt(),
            0xFFB22E54.toInt(), 0xFF596E16.toInt(), 0xFF176C8A.toInt(), 0xFF7650A1.toInt(),
            0xFF936622.toInt(), 0xFF326E39.toInt(), 0xFF9B3840.toInt(), 0xFF4F5D87.toInt(),
        )
    }
}

object FamilyIcons {
    /** Resolve from the whole roster, never from viewer-dependent name/order or available positions. */
    fun resolve(uids: Collection<String>, selections: Map<String, String>): Map<String, MemberAppearance> {
        val ordered = uids.distinct().sorted()
        val selected = ordered.mapNotNull { uid -> FamilyIcon.fromId(selections[uid])?.let { uid to it } }.toMap()
        val used = selected.values.toMutableSet()
        return ordered.mapIndexed { index, uid ->
            val icon = selected[uid] ?: run {
                val start = Math.floorMod(uid.hashCode(), FamilyIcon.entries.size)
                val available = FamilyIcon.entries.indices.asSequence()
                    .map { FamilyIcon.entries[(start + it) % FamilyIcon.entries.size] }
                    .firstOrNull { it !in used }
                (available ?: FamilyIcon.entries[start]).also { used += it }
            }
            uid to MemberAppearance(icon, index + 1)
        }.toMap()
    }
}
