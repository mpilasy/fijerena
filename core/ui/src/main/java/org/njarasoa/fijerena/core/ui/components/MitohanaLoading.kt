package org.njarasoa.fijerena.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.ui.R

@Composable
fun MitohanaLoading(
    modifier: Modifier = Modifier,
    role: TextRole = TextRole.Title,
    tone: TextTone = TextTone.Accent,
) {
    var dotCount by remember { mutableIntStateOf(1) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            dotCount = if (dotCount >= 5) 1 else dotCount + 1
        }
    }

    val dots = ".".repeat(dotCount)
    CinemaText(
        text = stringResource(R.string.loading_buffering) + dots,
        modifier = modifier,
        role = role,
        tone = tone,
    )
}
