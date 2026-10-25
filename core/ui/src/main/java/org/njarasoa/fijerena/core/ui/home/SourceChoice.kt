package org.njarasoa.fijerena.core.ui.home

/**
 * What a setting edited per source does when opened (content filters on a profile's page, the
 * phone's parental check): with no current source any more, the source is picked first, but
 * only when there is a choice (docs/plans/20261012_multi-source-search-history-plan.md → O4).
 */
sealed interface SourceChoice<out T> {
    /** No applicable source: the entry isn't offered. */
    data object None : SourceChoice<Nothing>

    /** One source: it opens straight away. */
    data class One<T>(
        val source: T,
    ) : SourceChoice<T>

    /** Two or more: a chooser lists them first. */
    data class Ask<T>(
        val sources: List<T>,
    ) : SourceChoice<T>

    companion object {
        fun <T> of(sources: List<T>): SourceChoice<T> =
            when (sources.size) {
                0 -> None
                1 -> One(sources.first())
                else -> Ask(sources)
            }
    }
}
