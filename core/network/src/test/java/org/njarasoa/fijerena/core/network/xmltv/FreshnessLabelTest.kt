package org.njarasoa.fijerena.core.network.xmltv

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.R

/**
 * The EPG header's one line. It read "Never refreshed" beside 810k programmes because a source
 * that had never run was folded into the same number as one that had — see
 * `docs/plans/id-type-safety-plan.md`, and the provider scoping in `EpgBrowserViewModel`.
 */
class FreshnessLabelTest {
    private val now = 1_787_184_000L

    private val context =
        mockk<Context>().apply {
            every { getString(R.string.epg_freshness_no_sources) } returns "No EPG sources"
            every { getString(R.string.epg_freshness_never_refreshed) } returns "Never refreshed"
            every { getString(R.string.epg_freshness_just_now) } returns "just now"
            every { getString(R.string.epg_freshness_minutes_ago_format, any()) } answers
                { "${secondArg<Array<Any>>()[0]}m ago" }
            every { getString(R.string.epg_freshness_hours_ago_format, any()) } answers
                { "${secondArg<Array<Any>>()[0]}h ago" }
            every { getString(R.string.epg_freshness_days_ago_format, any()) } answers
                { "${secondArg<Array<Any>>()[0]}d ago" }
            every { getString(R.string.epg_freshness_stale_suffix_format, any()) } answers
                { " • ${secondArg<Array<Any>>()[0]} stale" }
            every { getString(R.string.epg_freshness_never_run_suffix_format, any()) } answers
                { " • ${secondArg<Array<Any>>()[0]} never run" }
            every { getString(R.string.epg_freshness_updated_format, any()) } answers
                { "Updated ${secondArg<Array<Any>>()[0]}" }
        }

    private fun label(
        oldestIngestedAtMs: Long?,
        stale: Int = 0,
        neverRun: Int = 0,
    ) = freshnessLabel(context, oldestIngestedAtMs, now, stale, neverRun)

    @Test
    fun noEnabledSourcesSaysSo() {
        assertEquals("No EPG sources", label(null))
    }

    @Test
    fun nothingHasEverRunSaysNeverRefreshed() {
        assertEquals("Never refreshed", label(0L))
    }

    @Test
    fun aSourceThatNeverRanDoesNotHideOneThatJustDid() {
        // The reported case: one provider's source synced minutes ago, another had never run, and
        // the whole header read "Never refreshed".
        assertEquals("Updated 6m ago • 1 never run", label((now - 360) * 1000L, neverRun = 1))
    }

    @Test
    fun staleAndNeverRunAreCountedSeparately() {
        assertEquals("Updated 2h ago • 2 stale • 1 never run", label((now - 7200) * 1000L, stale = 2, neverRun = 1))
    }

    @Test
    fun everythingCurrentIsJustTheAge() {
        assertEquals("Updated just now", label((now - 30) * 1000L))
    }
}
