package org.njarasoa.fijerena.core.network.provider

/**
 * The short names source badges show ("BRS", "X4P") instead of the whole source name. A source's own
 * ([ProviderSettings.shortName]) wins; one without is given the shortest start of its name, three
 * letters or more, that no other source's name starts with: "bears" and "bearstv" become "BEARS"
 * and "BEARST", "xstream4k" alone "XST". Worked out again whenever a source is added or renamed, so
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
            val shortest =
                (minOf(MIN_AUTOMATIC, own.length)..own.length)
                    .map { own.take(it) }
                    .firstOrNull { start -> start !in taken && others.none { it.take(start.length) == start } }
            // Two sources with the same name: the second gets a number.
            result[source.id] = shortest
                ?: own.takeUnless { it in taken }
                ?: generateSequence(2) { it + 1 }.map { "$own$it" }.first { it !in taken }
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

    private fun lettersOf(name: String): String = name.filter(Char::isLetterOrDigit).uppercase().ifEmpty { name.trim().uppercase() }
}
