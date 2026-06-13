package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import org.njarasoa.fijerena.core.ui.R
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
        label = stringResource(R.string.provider_m3u_url_label),
        placeholder = stringResource(R.string.provider_m3u_url_placeholder),
        keyboardType = KeyboardType.Uri,
    )
}
