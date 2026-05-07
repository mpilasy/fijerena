package org.njarasoa.fijerena.feature.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.padding(CinemaSpacing.md)) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(CinemaSpacing.sm))
                content()
            }
        }
    }
}

fun formatProgrammeCount(count: Int): String =
    when {
        count >= 1000 -> "%.1fk".format(count / 1000.0)
        else -> count.toString()
    }
