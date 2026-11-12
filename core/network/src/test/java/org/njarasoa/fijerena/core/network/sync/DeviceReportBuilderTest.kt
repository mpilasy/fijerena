package org.njarasoa.fijerena.core.network.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.player.diagnostics.ProcessExits

/** A device's report from its logs: kinds, duplicates, caps, masking and Clear (the device diagnostics sync plan). */
class DeviceReportBuilderTest {
    private fun input(
        crashLog: List<String> = emptyList(),
        exits: List<ProcessExits.Exit> = emptyList(),
        connections: List<String> = emptyList(),
        clearedAt: Long = 0,
    ) = DeviceReportBuilder.Input(
        now = NOW,
        versionName = "0.9.20261111000000",
        lastUpdateTime = NOW - 2 * DAY,
        clearedAt = clearedAt,
        safeModeAt = 0,
        crashLog = crashLog,
        exits = exits,
        connections = connections,
    )

    private fun exit(
        at: Long,
        reason: String,
        description: String? = null,
    ) = ProcessExits.Exit(at, reason, description, "org.njarasoa.fijerena", null)

    @Test
    fun `crash log entries and exits get their kinds, and harmless exits stay out`() {
        val report =
            DeviceReportBuilder.build(
                input(
                    crashLog =
                        listOf(
                            "${NOW - 1_000} uncaught on main · screen: Settings\njava.lang.IllegalStateException: boom\n\tat a.b(C.kt:1)",
                            "${NOW - 2_000} guide refresh failed\nHTTP 404",
                        ),
                    exits =
                        listOf(
                            exit(NOW - 3_000, "ANR", "Input dispatching timed out"),
                            exit(NOW - 4_000, "LOW_MEMORY"),
                            exit(NOW - 5_000, "CRASH_NATIVE"),
                            exit(NOW - 6_000, "USER_REQUESTED"),
                            exit(NOW - 7_000, "PACKAGE_UPDATED"),
                        ),
                ),
            )

        assertEquals(
            listOf(
                DeviceReport.KIND_CRASH,
                DeviceReport.KIND_ERROR,
                DeviceReport.KIND_ANR,
                DeviceReport.KIND_KILLED,
                DeviceReport.KIND_CRASH,
            ),
            report.entries.map { it.kind },
        )
        assertEquals(DeviceReport.Counts(crashes = 2, anrs = 1, killed = 1, errors = 1), report.counts7d)
        val crash = report.entries.first()
        assertEquals("uncaught on main", crash.source)
        assertEquals("IllegalStateException: boom", crash.message)
        assertEquals("", crash.cause)
        assertEquals("Settings", crash.screen)
        assertTrue(crash.afterUpdate)
        assertEquals("Input dispatching timed out", report.entries[2].message)
        assertEquals("0.9.20261111000000", report.versionName)
        assertTrue(report.isReported)
    }

    @Test
    fun `an exit CRASH within 10 s of an uncaught exception is the same crash`() {
        val report =
            DeviceReportBuilder.build(
                input(
                    crashLog = listOf("${NOW - 60_000} uncaught on main\njava.lang.RuntimeException: x\n\tat a.b(C.kt:1)"),
                    exits = listOf(exit(NOW - 55_000, "CRASH"), exit(NOW - 10 * 60_000, "CRASH")),
                ),
            )

        assertEquals(2, report.entries.size)
        assertEquals("uncaught on main", report.entries[0].source)
        assertEquals("exit CRASH", report.entries[1].source)
        assertEquals(2, report.counts7d.crashes)
    }

    @Test
    fun `the cause shows when it is not the message`() {
        val trace =
            "java.io.IOException: sync failed\n\tat a.b(C.kt:1)\nCaused by: java.net.SocketTimeoutException: timeout\n\tat c.d(E.kt:2)"

        val entry = DeviceReportBuilder.build(input(crashLog = listOf("${NOW - 1} catalogue sync failed: My TV\n$trace"))).entries.single()

        assertEquals("IOException: sync failed", entry.message)
        assertEquals("SocketTimeoutException: timeout", entry.cause)
    }

    @Test
    fun `only the newest 20 of the last 14 days stay, the counts cover 7 days of all of them`() {
        val log = (1..30).map { "${NOW - it * 60_000L} error $it\nfailed" } + "${NOW - 15 * DAY} old error\nfailed"
        val week = listOf("${NOW - 8 * DAY} last week\nfailed")

        val report = DeviceReportBuilder.build(input(crashLog = log + week))

        assertEquals(DeviceReportBuilder.MAX_ENTRIES, report.entries.size)
        assertEquals("error 1", report.entries.first().source)
        assertEquals("error 20", report.entries.last().source)
        assertEquals(30, report.counts7d.errors)
        assertTrue(report.entries.none { it.source == "old error" })
    }

    @Test
    fun `a long report drops its oldest entries to fit 12,000 characters, and every text is cut`() {
        val long = "x".repeat(1_000)
        val trace = "java.io.IOException: ${"m".repeat(
            300,
        )}\n\tat a.b(C.kt:1)\nCaused by: java.lang.Exception: ${"c".repeat(300)}\n\tat c.d(E.kt:2)"
        val log = (1..20).map { "${NOW - it * 60_000L} source $it $long · screen: $long\n$trace" }

        val report = DeviceReportBuilder.build(input(crashLog = log))
        val encoded = DeviceReportBuilder.encode(report)

        assertTrue(encoded.length.toString(), encoded.length <= DeviceReportBuilder.MAX_ENCODED_CHARS)
        assertTrue(report.entries.size in 1 until 20)
        assertTrue(
            report.entries
                .first()
                .source
                .startsWith("source 1 "),
        )
        val first = report.entries.first()
        assertEquals(DeviceReportBuilder.SOURCE_MAX, first.source.length)
        assertEquals(DeviceReportBuilder.MESSAGE_MAX, first.message.length)
        assertEquals(DeviceReportBuilder.MESSAGE_MAX, first.cause.length)
        assertEquals(DeviceReportBuilder.SCREEN_MAX, first.screen.length)
        assertTrue(first.source.endsWith("…"))
    }

    @Test
    fun `hosts, logins and tokens are masked in every text`() {
        val entry =
            DeviceReportBuilder
                .build(
                    input(
                        crashLog =
                            listOf(
                                "${NOW - 1} playback failed for http://panel.example/live/alice/s3cret/1.ts\nBearer abc.def GET https://api.example/x?token=t0k",
                            ),
                    ),
                ).entries
                .single()

        assertEquals("playback failed for http://***/live/***/***/1.ts", entry.source)
        assertEquals("Bearer *** GET https://***/x?token=***", entry.message)
        val all = DeviceReportBuilder.encode(DeviceReportBuilder.build(input(crashLog = listOf("${NOW - 1} x\nhttp://panel.example/a"))))
        assertFalse(all, all.contains("panel.example"))
    }

    @Test
    fun `Clear leaves out everything older, the exits and failed calls too`() {
        val report =
            DeviceReportBuilder.build(
                input(
                    crashLog = listOf("${NOW - 1_000} new\nfailed", "${NOW - 3_000} before clear\nfailed"),
                    exits = listOf(exit(NOW - 4_000, "ANR")),
                    connections = listOf("${NOW - 5_000} No answer from p.example\nhttp://p.example\nIOException: x"),
                    clearedAt = NOW - 2_000,
                ),
            )

        assertEquals(listOf("new"), report.entries.map { it.source })
        assertEquals(DeviceReport.Counts(errors = 1), report.counts7d)
        assertEquals(0, report.failedCalls24h)
    }

    @Test
    fun `failed server calls of the last 24 h are counted, stream events are not`() {
        val report =
            DeviceReportBuilder.build(
                input(
                    connections =
                        listOf(
                            "${NOW - 1_000} No answer from p.example\nhttp://p.example\nIOException: x",
                            "${NOW - 2_000} HTTP 403 from p.example\nhttp://p.example\nClientRequestException",
                            "${NOW - 3_000} Stream error\nhttp://p.example/live/1.ts\ndetail",
                            "${NOW - 2 * DAY} HTTP 500 from p.example\nhttp://p.example\nx",
                        ),
                ),
            )

        assertEquals(2, report.failedCalls24h)
    }

    @Test
    fun `an entry from before the last update is not this build`() {
        val entry = DeviceReportBuilder.build(input(crashLog = listOf("${NOW - 3 * DAY} old\nfailed"))).entries.single()

        assertFalse(entry.afterUpdate)
    }

    @Test
    fun `a report survives the JSON round trip, and missing fields read as defaults`() {
        val report = DeviceReportBuilder.build(input(crashLog = listOf("${NOW - 1} guide refresh failed · screen: Home\nHTTP 404")))

        assertEquals(report, SyncPayloads.json.decodeFromString(DeviceReport.serializer(), DeviceReportBuilder.encode(report)))
        val older =
            SyncPayloads.json.decodeFromString(
                DeviceReport.serializer(),
                """{"versionName":"0.9.1","entries":[{"kind":"later"}],"gpu":1}""",
            )
        assertEquals(DeviceReport(versionName = "0.9.1", entries = listOf(DeviceReport.Entry(kind = "later"))), older)
        assertFalse(DeviceReport().isReported)
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
        const val NOW = 1_800_000_000_000L
    }
}
