package org.njarasoa.fijerena.core.network.xmltv

import org.junit.Test
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class EpgFileManagerTest {
    @Test
    fun testDownloadBlocking() = runBlocking {
        // Just demonstrating that a blocking call blocks the dispatcher if not properly isolated.
        val time = measureTimeMillis {
            val jobs = (1..5).map {
                launch(Dispatchers.Default) {
                    // simulate blocking
                    Thread.sleep(100)
                }
            }
            jobs.forEach { it.join() }
        }
        println("Blocking simulated time: $time ms")
    }
}
