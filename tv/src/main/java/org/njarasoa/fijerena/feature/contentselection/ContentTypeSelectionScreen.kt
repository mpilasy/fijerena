@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.contentselection

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import org.njarasoa.fijerena.core.navigation.ContentType
import org.njarasoa.fijerena.core.network.AppSettings

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
                horizontal = (configuration.screenWidthDp * 0.05).dp,
                vertical = (configuration.screenHeightDp * 0.05).dp
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with provider name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                    modifier = Modifier.padding(bottom = 48.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
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
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
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
            .width(400.dp)
            .height(80.dp),
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = Color.White,
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            focusedContentColor = Color.White
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
    }
}
