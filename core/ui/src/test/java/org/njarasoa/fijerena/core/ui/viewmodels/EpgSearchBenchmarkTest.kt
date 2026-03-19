package org.njarasoa.fijerena.core.ui.viewmodels

import org.junit.Test
import org.njarasoa.fijerena.core.player.domain.MediaType
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.model.EpgChannelRow
import org.njarasoa.fijerena.core.player.model.EpgProgram
import java.time.LocalDate
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope

class EpgSearchBenchmarkTest {

    @Test
    fun benchmarkSearch() = runBlocking {
        val items = (1..500).map {
            MediaItem(id = it.toString(), name = "Channel $it", mediaType = MediaType.LIVE_CHANNEL, categoryId = "1")
        }
        val rows = items.map { item ->
            val programs = (1..1000).map {
                EpgProgram(id = it.toString(), title = "Program $it for \${item.name}", start = "0", end = "0")
            }
            EpgChannelRow(channel = item, programs = programs)
        }

        val state = EpgViewModel.UiState.Success(
            channelRows = rows,
            timeSlots = emptyList(),
            currentTimeSlot = 0,
            selectedDate = LocalDate.now()
        )

        val query = "Program 50 "
        val now = 0L

        // Warmup
        for (i in 1..10) {
            val results = buildList<EpgViewModel.EpgSearchResult> {
                for (row in state.channelRows) {
                    for (program in row.programs) {
                        if (program.title.contains(query, ignoreCase = true)) {
                            add(EpgViewModel.EpgSearchResult(
                                program = program,
                                channel = row.channel,
                                isCurrent = now in program.startTime..program.endTime
                            ))
                        }
                    }
                }
            }
        }

        // Benchmark old
        val timeOld = measureTimeMillis {
            for (i in 1..10) {
                val results = buildList<EpgViewModel.EpgSearchResult> {
                    for (row in state.channelRows) {
                        for (program in row.programs) {
                            if (program.title.contains(query, ignoreCase = true)) {
                                add(EpgViewModel.EpgSearchResult(
                                    program = program,
                                    channel = row.channel,
                                    isCurrent = now in program.startTime..program.endTime
                                ))
                            }
                        }
                    }
                }
                val (current, others) = results.partition { it.isCurrent }
                val finalResults = current + others
            }
        }
        println("Old time: ${timeOld}ms")

        // Parallel using indexOf
        val timeParallelIndexOf = measureTimeMillis {
            for (i in 1..10) {
                coroutineScope {
                    val chunkedRows = state.channelRows.chunked(maxOf(1, state.channelRows.size / Runtime.getRuntime().availableProcessors()))
                    val results = chunkedRows.map { chunk ->
                        async(Dispatchers.Default) {
                            val current = mutableListOf<EpgViewModel.EpgSearchResult>()
                            val others = mutableListOf<EpgViewModel.EpgSearchResult>()
                            for (row in chunk) {
                                val channel = row.channel
                                for (program in row.programs) {
                                    if (program.title.indexOf(query, ignoreCase = true) >= 0) {
                                        val isCurrent = now in program.startTime..program.endTime
                                        val res = EpgViewModel.EpgSearchResult(program, channel, isCurrent)
                                        if (isCurrent) current.add(res) else others.add(res)
                                    }
                                }
                            }
                            Pair(current, others)
                        }
                    }.awaitAll()

                    val currentFinal = mutableListOf<EpgViewModel.EpgSearchResult>()
                    val othersFinal = mutableListOf<EpgViewModel.EpgSearchResult>()
                    for (res in results) {
                        currentFinal.addAll(res.first)
                        othersFinal.addAll(res.second)
                    }
                    val finalResults = currentFinal + othersFinal
                }
            }
        }
        println("Parallel indexOf time: ${timeParallelIndexOf}ms")
    }
}
