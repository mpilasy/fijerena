package org.njarasoa.fijerena.core.network.provider

/**
 * The short names source badges show ("BRS", "X4P") instead of the whole source name. A source's own
 * ([ProviderSettings.shortName]) wins; one without is given the shortest start of its name, three
 * letters or more, that no other source's name starts with: "bears" and "bearstv" become "BEARS"
 * and "BEATV", "xstream4k" alone "XST". Never longer than [MAX_LENGTH]: names that share a longer
 * start keep their first three letters and the first one that tells them apart — "xstream4kb" and
 * "xstream4kp" become "XSTB" and "XSTP". Worked out again whenever a source is added or renamed, so
 * only the names the viewer typed stay put.
 */
object SourceShortNames {
    /** Longest short name the viewer can type. */
    const val MAX_LENGTH = 5

    private const val MIN_AUTOMATIC = 3

    data class Source(
        val id: Long,
        val name: String,
        /** The viewer's short name, blank when they haven't set one. */
        val shortName: String,
    )

    /** Every source's short name, by source id. */
    fun of(sources: List<Source>): Map<Long, String> {
        val result = mutableMapOf<Long, String>()
        sources.filter { it.shortName.isNotBlank() }.forEach { result[it.id] = it.shortName.trim() }
        val automatic = sources.filter { it.shortName.isBlank() }.sortedBy { it.id }
        val letters = automatic.associate { it.id to lettersOf(it.name) }
        for (source in automatic) {
            val own = letters.getValue(source.id)
            val others = automatic.filter { it.id != source.id }.map { letters.getValue(it.id) }
            val taken = result.values.map { it.uppercase() }.toSet()
            val unique = { candidate: String -> candidate !in taken && others.none { it.take(candidate.length) == candidate } }
            val shortest =
                (minOf(MIN_AUTOMATIC, own.length)..minOf(MAX_LENGTH, own.length))
                    .map { own.take(it) }
                    .firstOrNull(unique)
                    ?: distinguished(own, others)?.takeIf { it !in taken }
            // Two sources with the same name: the first three letters, the second with a number.
            val base = own.take(MIN_AUTOMATIC)
            result[source.id] = shortest
                ?: own.takeIf { it.length <= MAX_LENGTH && it !in taken }
                ?: base.takeUnless { it in taken }
                ?: generateSequence(2) { it + 1 }.map { "$base$it" }.first { it !in taken }
        }
        return result
    }

    /**
     * Whether [shortName] can be [id]'s: blank (automatic) always can; otherwise at most
     * [MAX_LENGTH] characters and not what another source's badge shows now, typed or automatic
     * (any case), so taking it never renames another source's badge.
     */
    fun isFree(
        shortName: String,
        id: Long,
        sources: List<Source>,
    ): Boolean {
        val wanted = shortName.trim()
        if (wanted.isEmpty()) return true
        if (wanted.length > MAX_LENGTH) return false
        return of(sources).filterKeys { it != id }.values.none { it.equals(wanted, ignoreCase = true) }
    }

    /**
     * [own]'s first three letters and the first letter where it differs from every other name that
     * shares its start, up to [MAX_LENGTH] in all: "XSTREAM4KB" next to "XSTREAM4KP" → "XSTB". Null
     * when nothing tells it apart (the same name twice).
     */
    private fun distinguished(
        own: String,
        others: List<String>,
    ): String? {
        val rivals = others.filter { it.take(MIN_AUTOMATIC) == own.take(MIN_AUTOMATIC) }
        val at = (MIN_AUTOMATIC until own.length).firstOrNull { i -> rivals.none { it.length > i && it.take(i + 1) == own.take(i + 1) } }
        return at?.let { (own.take(MIN_AUTOMATIC) + own.substring(it)).take(MAX_LENGTH) }
    }

    private fun lettersOf(name: String): String = name.filter(Char::isLetterOrDigit).uppercase().ifEmpty { name.trim().uppercase() }
}
