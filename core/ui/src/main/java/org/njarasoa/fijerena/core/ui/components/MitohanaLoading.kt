package org.njarasoa.fijerena.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.ui.R

@Composable
fun MitohanaLoading(
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.Unspecified,
) {
    var dotCount by remember { mutableIntStateOf(1) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            dotCount = if (dotCount >= 5) 1 else dotCount + 1
        }
    }

    val dots = ".".repeat(dotCount)
    Text(
        text = stringResource(R.string.loading_buffering) + dots,
        modifier = modifier,
        style = style,
        color = color,
    )
}
