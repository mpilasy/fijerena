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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import org.njarasoa.fijerena.core.navigation.ContentType

/**
 * Content type selection screen - allows users to choose between Live TV, Movies, or TV Shows.
 */
@Composable
fun ContentTypeSelectionScreen(
    onContentTypeSelected: (ContentType) -> Unit,
    onLogout: () -> Unit,
    onSettings: () -> Unit
) {
    val configuration = LocalConfiguration.current

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
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IPTV.atr",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Logout")
                }
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
            focusedContainerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge
        )
    }
}
