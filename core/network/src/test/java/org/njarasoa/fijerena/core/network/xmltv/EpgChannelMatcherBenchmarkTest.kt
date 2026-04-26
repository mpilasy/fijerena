package org.njarasoa.fijerena.core.network.xmltv

import org.junit.Test
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity
import kotlin.system.measureTimeMillis

class EpgChannelMatcherBenchmarkTest {

    @Test
    fun benchmarkMatcher() {
        // Typical M3U list has many streams, maybe 100k
        val streams = (1..100000).map {
            XtreamStreamEntity(
                streamId = it,
                name = "Random Channel $it HD",
                categoryId = "1",
                providerId = 1L,
                epgChannelId = "epg_$it",
                streamType = "live",
                streamIcon = "",
                num = it,
                added = "0",
                type = XtreamStreamEntity.TYPE_LIVE
            )
        }

        val matcher = EpgChannelMatcher(streams)

        val queries = (1..5000).map {
            "Non Existent Channel ${it * 50} FHD"
        }

        var matches = 0
        val time = measureTimeMillis {
            for (query in queries) {
                if (matcher.match("epg_unknown", query) != null) {
                    matches++
                }
            }
        }
        println("Matched: $matches")
        println("Time: $time ms")
    }
}
