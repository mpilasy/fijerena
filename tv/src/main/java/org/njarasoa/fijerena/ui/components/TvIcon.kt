package org.njarasoa.fijerena.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.njarasoa.fijerena.core.ui.components.CinemaIcon
import org.njarasoa.fijerena.core.ui.components.TextTone

/**
 * [CinemaIcon] on TV: [TextTone.Inherit] reads the TV Surface's content colour (TV Material's
 * `LocalContentColor`), so an icon in a focused TV button turns dark on the white focus.
 */
@Composable
fun TvIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tone: TextTone = TextTone.Inherit,
) {
    CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides androidx.tv.material3.LocalContentColor.current,
    ) {
        CinemaIcon(imageVector = imageVector, contentDescription = contentDescription, modifier = modifier, tone = tone)
    }
}
