package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.player.diagnostics.TraceCauses
import org.njarasoa.fijerena.core.ui.viewmodels.DiagnosticsViewModel.Entry

/** The Logs search: title, detail and causes match, case ignored; a blank query keeps all. */
class DiagnosticsFilterTest {
    private val sync = Entry(timestampMs = 3, title = "2026-11-03 10:00:00 · catalog sync", detail = "plain message")
    private val http = Entry(timestampMs = 2, title = "2026-11-03 09:00:00 · HTTP 403", detail = "from panel-b.example")
    private val crash =
        Entry(
            timestampMs = 1,
            title = "2026-11-03 08:00:00 · crash",
            detail = "trace",
            causes = TraceCauses(listOf("IOException: sync failed", "SocketTimeoutException: timeout")),
        )
    private val all = listOf(sync, http, crash)

    @Test
    fun `an empty or blank query keeps every entry`() {
        assertEquals(all, DiagnosticsViewModel.filter(all, ""))
        assertEquals(all, DiagnosticsViewModel.filter(all, "   "))
    }

    @Test
    fun `the title matches, case ignored`() {
        assertEquals(listOf(sync), DiagnosticsViewModel.filter(all, "CATALOG"))
    }

    @Test
    fun `the detail matches, case ignored`() {
        assertEquals(listOf(http), DiagnosticsViewModel.filter(all, "Panel-B"))
    }

    @Test
    fun `the chain of causes matches, case ignored`() {
        assertEquals(listOf(crash), DiagnosticsViewModel.filter(all, "sockettimeout"))
    }

    @Test
    fun `the query is trimmed and nothing matching gives an empty list`() {
        assertEquals(listOf(http), DiagnosticsViewModel.filter(all, " 403 "))
        assertEquals(emptyList<Entry>(), DiagnosticsViewModel.filter(all, "no such text"))
    }
}
