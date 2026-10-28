package org.njarasoa.fijerena.core.player.diagnostics

/**
 * The exception chain of a stack trace, as Logs shows it: one item per exception (the top one, then
 * each "Caused by:"), with the simple class name and the message, and no "at" frames or
 * "... N more" lines. Copy and Share keep the full trace.
 */
data class TraceCauses(
    /** Top exception first, root cause last. An item has more than one line when its message has. */
    val chain: List<String>,
) {
    /** The first line of the last cause: what Logs shows on a closed entry. */
    val rootCause: String get() = chain.last().substringBefore('\n')

    companion object {
        /** Characters kept of one exception's text; a longer one ends in "…". */
        const val MAX_CHARS = 400

        private const val CAUSED_BY = "Caused by: "

        // Indented lines that are part of the trace, not of a message: frames, "... N more", and
        // suppressed exceptions with their own causes.
        private val TRACE_LINE = Regex("""^\s+(at \S|\.\.\. \d+ more|Suppressed: |Caused by: )""")

        private val CLASS_NAME = Regex("""^[\w$]+(\.[\w$]+)+$""")

        /** The chain in [trace], or null when [trace] has no stack frame ("\tat …"), so it is a plain message. */
        fun parse(trace: String): TraceCauses? {
            val lines = trace.lines()
            if (lines.none { it.startsWith("\tat ") }) return null
            val items = mutableListOf<MutableList<String>>()
            // True while the lines that follow belong to the last exception's message.
            var inMessage = false
            for (line in lines) {
                when {
                    line.startsWith(CAUSED_BY) -> {
                        items += mutableListOf(line.removePrefix(CAUSED_BY))
                        inMessage = true
                    }

                    TRACE_LINE.containsMatchIn(line) -> {
                        inMessage = false
                    }

                    items.isEmpty() -> {
                        if (line.isNotBlank()) {
                            items += mutableListOf(line)
                            inMessage = true
                        }
                    }

                    inMessage -> {
                        items.last() += line
                    }
                }
            }
            if (items.isEmpty()) return null
            return TraceCauses(items.map { item -> cut(shortName(item.joinToString("\n").trimEnd())) })
        }

        /** "java.net.SocketTimeoutException: timeout" → "SocketTimeoutException: timeout". */
        private fun shortName(text: String): String {
            val firstLine = text.substringBefore('\n')
            val className = if (": " in firstLine) firstLine.substringBefore(": ") else firstLine
            if (!CLASS_NAME.matches(className)) return text
            return className.substringAfterLast('.') + text.removePrefix(className)
        }

        private fun cut(text: String): String = if (text.length <= MAX_CHARS) text else text.take(MAX_CHARS).trimEnd() + "…"
    }
}
