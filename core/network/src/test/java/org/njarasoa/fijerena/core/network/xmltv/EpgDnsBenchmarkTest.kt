package org.njarasoa.fijerena.core.network.xmltv

import org.junit.Test
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EpgDnsBenchmarkTest {
    @Test
    fun testDnsBlocking() = runBlocking {
        println("Starting DNS benchmark...")

        // Using a non-existent domain to trigger a longer resolution or failure
        val domain = "nonexistent.example.com.invalid"

        // Baseline: simulating concurrent DNS lookups without IO dispatcher
        val timeDefault = measureTimeMillis {
            val jobs = (1..20).map {
                launch(Dispatchers.Default) {
                    try {
                        java.net.InetAddress.getByName(domain)
                    } catch (e: Exception) {}
                }
            }
            jobs.forEach { it.join() }
        }

        println("Time without IO dispatcher: $timeDefault ms")

        // Improvement: simulating concurrent DNS lookups with IO dispatcher
        val timeIO = measureTimeMillis {
            val jobs = (1..20).map {
                launch(Dispatchers.Default) {
                    withContext(Dispatchers.IO) {
                        try {
                            java.net.InetAddress.getByName(domain)
                        } catch (e: Exception) {}
                    }
                }
            }
            jobs.forEach { it.join() }
        }

        println("Time with IO dispatcher: $timeIO ms")
    }
}
