@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.contentselection

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import org.njarasoa.fijerena.core.navigation.ContentType
import org.njarasoa.fijerena.core.network.AppSettings
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.*
import androidx.compose.ui.graphics.Color

/**
 * Content type selection screen - allows users to choose between Live TV, Movies, or TV Shows.
 */
@Composable
fun ContentTypeSelectionScreen(
    onContentTypeSelected: (ContentType) -> Unit,
    onSettings: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val providerName by remember { mutableStateOf(appSettings.providerName) }

    // 5% padding for TV overscan safety
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = Spacing.tvSafeHorizontal(configuration.screenWidthDp),
                vertical = Spacing.tvSafeVertical(configuration.screenHeightDp)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with provider name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xxl),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "IPTV.atr",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = providerName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.87f)
                )
            }

            // Content type selection
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Select Content Type",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = Spacing.xxl)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ContentTypeButton(
                        text = "📺 ${ContentType.LIVE_TV.displayName}",
                        onClick = { onContentTypeSelected(ContentType.LIVE_TV) }
                    )

                    ContentTypeButton(
                        text = "🎬 ${ContentType.MOVIES.displayName}",
                        onClick = { onContentTypeSelected(ContentType.MOVIES) }
                    )

                    ContentTypeButton(
                        text = "📺 ${ContentType.TV_SHOWS.displayName}",
                        onClick = { onContentTypeSelected(ContentType.TV_SHOWS) }
                    )
                }
            }
        }

        // Settings gear icon at bottom left
        CinemaIconButton(
            onClick = onSettings,
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = CinemaAccentLight
                )
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Spacing.md)
        )
    }
}

@Composable
private fun ContentTypeButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(600.dp)
            .height(64.dp),
        colors = ButtonDefaults.colors(
            containerColor = CinemaSurfaceVariant,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaSurface,
            focusedContentColor = CinemaTextPrimary
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 5.dp, color = CinemaAccentLight)
            )
        ),
        scale = ButtonDefaults.scale(
            scale = 1.0f,
            focusedScale = 1.12f,
            pressedScale = 0.95f
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
    }
}
