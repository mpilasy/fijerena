package org.njarasoa.fijerena.core.player.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/** Logs folds a stack trace to its exception chain; Copy and Share keep the full trace. */
class TraceCausesTest {
    private val syncTrace =
        """
        org.njarasoa.fijerena.core.network.sync.CatalogSyncException: 1 catalog sync task(s) failed: series: Unexpected JSON token
        	at org.njarasoa.fijerena.core.network.sync.CatalogSync.run(CatalogSync.kt:120)
        	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
        Caused by: kotlinx.serialization.json.JsonDecodingException: Unexpected JSON token at offset 0: Expected start of the array '[', but had 'EOF' instead at path: ${'$'}
        	at kotlinx.serialization.json.internal.JsonExceptionsKt.JsonDecodingException(JsonExceptions.kt:24)
        	at kotlinx.serialization.json.internal.StreamingJsonDecoder.decodeSerializableValue(StreamingJsonDecoder.kt:72)
        	... 2 more
        Caused by: java.net.SocketTimeoutException: timeout
        	at okio.SocketAsyncTimeout.newTimeoutException(JvmOkio.kt:147)
        	at okio.AsyncTimeout.access${'$'}newTimeoutException(AsyncTimeout.kt:158)
        	... 4 more
        """.trimIndent()

    private val ktorTrace =
        """
        io.ktor.client.plugins.ServerResponseException: Server error(GET https://panel.example/player_api.php?action=get_series: 502 Bad Gateway. Text: "<html>
        <head><title>502 Bad Gateway</title></head>
        <body>nginx</body>
        </html>
        "
        	at io.ktor.client.plugins.DefaultResponseValidationKt${'$'}addDefaultResponseValidation${'$'}1${'$'}1.invokeSuspend(DefaultResponseValidation.kt:54)
        	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
        """.trimIndent()

    @Test
    fun `a chain of causes shows its last cause closed and one line per exception open`() {
        val causes = TraceCauses.parse(syncTrace)!!

        assertEquals("SocketTimeoutException: timeout", causes.rootCause)
        assertEquals(
            listOf(
                "CatalogSyncException: 1 catalog sync task(s) failed: series: Unexpected JSON token",
                "JsonDecodingException: Unexpected JSON token at offset 0: Expected start of the array '[', " +
                    "but had 'EOF' instead at path: ${'$'}",
                "SocketTimeoutException: timeout",
            ),
            causes.chain,
        )
    }

    @Test
    fun `a message on more than one line keeps its lines open and its first line closed`() {
        val causes = TraceCauses.parse(ktorTrace)!!

        assertEquals(1, causes.chain.size)
        assertEquals(
            "ServerResponseException: Server error(GET https://panel.example/player_api.php?action=get_series: " +
                "502 Bad Gateway. Text: \"<html>",
            causes.rootCause,
        )
        assertTrue(causes.chain.single().endsWith("<body>nginx</body>\n</html>\n\""))
        assertTrue(causes.chain.none { it.contains("\tat ") })
    }

    @Test
    fun `a real throwable's trace parses to its causes`() {
        val error = IOException("sync failed", SocketTimeoutException("timeout"))

        val causes = TraceCauses.parse(error.stackTraceToString())!!

        assertEquals(listOf("IOException: sync failed", "SocketTimeoutException: timeout"), causes.chain)
    }

    @Test
    fun `an exception without a message shows its simple class name`() {
        val causes = TraceCauses.parse("java.lang.IllegalStateException\n\tat a.B.c(B.kt:1)")!!

        assertEquals("IllegalStateException", causes.rootCause)
    }

    @Test
    fun `suppressed exceptions are not part of the chain`() {
        val error = IOException("top").apply { addSuppressed(IllegalStateException("aside\nsecond line")) }

        val causes = TraceCauses.parse(error.stackTraceToString())!!

        assertEquals(listOf("IOException: top"), causes.chain)
    }

    @Test
    fun `text without a stack frame is a plain message`() {
        assertNull(TraceCauses.parse("Sync of series failed: HTTP 500"))
        assertNull(TraceCauses.parse(""))
        // An ANR thread dump indents its frames with spaces, not a tab, and has no exception line.
        assertNull(TraceCauses.parse("\"main\" prio=5 tid=1 Native\n  at android.os.MessageQueue.nativePollOnce(Native method)"))
    }

    @Test
    fun `a very long message is cut`() {
        val causes = TraceCauses.parse("java.io.IOException: ${"x".repeat(2000)}\n\tat a.B.c(B.kt:1)")!!

        assertEquals(TraceCauses.MAX_CHARS + 1, causes.rootCause.length)
        assertTrue(causes.rootCause.endsWith("…"))
    }
}
