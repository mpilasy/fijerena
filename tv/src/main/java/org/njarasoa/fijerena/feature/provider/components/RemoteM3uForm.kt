package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import org.njarasoa.fijerena.ui.components.ReadOnlyFieldWithEdit
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.scaled

@Composable
fun RemoteM3uForm(
    url: String,
    onUrlChange: (String) -> Unit,
    onErrorChange: (String?) -> Unit,
) {
    val scale = LocalUiScale.current

    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    ReadOnlyFieldWithEdit(
        value = url,
        onValueChange = {
            onUrlChange(it)
            onErrorChange(null)
        },
        label = "M3U Playlist URL",
        placeholder = "https://example.com/playlist.m3u",
        keyboardType = KeyboardType.Uri,
    )
}
