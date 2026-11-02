package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * A short name on [maxLines] lines that breaks only between words: a name on a narrow tile (a
 * followed team, "Manchester City"). A word that is wider than the line ends with "…" on its own
 * line; the words that do not fit on the last line end it with "…". Every line is kept, so the
 * tiles of a row are one height. Plain [CinemaText] broke the word ("Manchest" / "er City").
 */
@Composable
fun CinemaWordLines(
    text: String,
    role: TextRole,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    textAlign: TextAlign? = null,
) {
    val style = textStyleOf(role)
    val measurer = rememberTextMeasurer()
    val shown = inLookCase(text, role)
    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = text }) {
        val width = constraints.maxWidth
        val lines =
            remember(shown, style, width, maxLines) {
                wordLines(shown, maxLines) { line -> measurer.measure(line, style, maxLines = 1, softWrap = false).size.width <= width }
            }
        Column(modifier = Modifier.fillMaxWidth()) {
            repeat(maxLines) { index ->
                CinemaText(
                    text = lines.getOrElse(index) { "" },
                    role = role,
                    textAlign = textAlign,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    keepCase = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * [text]'s words put on at most [maxLines] lines: each line takes the words that [fits] allows (at
 * least one word), the last line takes all the words that are left.
 */
internal fun wordLines(
    text: String,
    maxLines: Int,
    fits: (String) -> Boolean,
): List<String> {
    val words = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    val lines = mutableListOf<String>()
    var next = 0
    while (next < words.size && lines.size < maxLines - 1) {
        var line = words[next++]
        while (next < words.size && fits("$line ${words[next]}")) line = "$line ${words[next++]}"
        lines += line
    }
    if (next < words.size) lines += words.drop(next).joinToString(" ")
    return lines
}

private val WHITESPACE = Regex("\\s+")
