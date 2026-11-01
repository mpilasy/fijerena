package org.njarasoa.fijerena.core.ui.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The app's icon, everywhere: [tone] gives its colour from [org.njarasoa.fijerena.core.ui.theme.CinemaTheme],
 * as for [CinemaText]. [TextTone.Inherit] (the default) is the colour around it, so an icon in a
 * button follows the button's content colour (dark on a focused button's light ground). The caller
 * never sets a colour itself: a changed colour theme reaches every icon.
 */
@Composable
fun CinemaIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tone: TextTone = TextTone.Inherit,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = colorOf(tone),
    )
}
