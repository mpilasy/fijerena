package org.njarasoa.fijerena.core.network.local

import org.junit.Test
import kotlin.system.measureTimeMillis

class M3uParserBenchmarkTest {

    @Test
    fun benchmarkParseLargeM3u() {
        val entryCount = 10000
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")

        for (i in 0 until entryCount) {
            sb.append("#EXTINF:-1 tvg-id=\"channel$i\" tvg-name=\"Channel $i\" tvg-logo=\"http://example.com/logo$i.png\" group-title=\"Group ${i % 10}\",Channel $i\n")
            sb.append("http://example.com/stream$i.m3u8\n")
        }

        val content = sb.toString()

        // Warmup
        repeat(5) {
            M3uParser.parse(content)
        }

        var totalTime = 0L
        val iterations = 10

        // Measure
        repeat(iterations) {
            val time = measureTimeMillis {
                M3uParser.parse(content)
            }
            totalTime += time
        }

        println("BENCHMARK_RESULT: Parsing $entryCount entries took ${totalTime / iterations} ms on average")
    }
}
