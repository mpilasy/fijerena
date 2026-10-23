package org.njarasoa.fijerena.core.network.xmltv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import kotlin.system.measureTimeMillis

class EpgFileManagerTest {
    @Test
    fun testDownloadBlocking() =
        runBlocking {
            // Demonstrate the dispatcher exhaustion issue that enqueue() aims to fix
            val jobs =
                (1..50).map {
                    launch(Dispatchers.Default) {
                        withContext(Dispatchers.IO) {
                            Thread.sleep(100) // simulate blocking IO .execute()
                        }
                    }
                }
            val timeSync =
                measureTimeMillis {
                    jobs.forEach { it.join() }
                }
            println("Simulated sync thread execution time: $timeSync ms")

            val asyncJobs =
                (1..50).map {
                    launch(Dispatchers.Default) {
                        // simulate OkHttp enqueue
                        delay(100)
                        withContext(Dispatchers.IO) {
                            Thread.sleep(1) // Just stream reading simulation
                        }
                    }
                }
            val timeAsync =
                measureTimeMillis {
                    asyncJobs.forEach { it.join() }
                }
            println("Simulated async thread execution time: $timeAsync ms")
        }
}
