@file:OptIn(ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.contentselection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.navigation.ContentType
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.ui.components.buttons.CinemaIconButton
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CinemaSurface
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions
import org.njarasoa.fijerena.ui.theme.TvFocusTokens

/**
 * Content type selection screen - allows users to choose between Live TV, Movies, or TV Shows.
 */
@Composable
fun ContentTypeSelectionScreen(
    onContentTypeSelected: (ContentType) -> Unit,
    onSettings: () -> Unit,
    onProviderChanged: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    var providerName by remember { mutableStateOf("") }
    var providerType by remember { mutableStateOf("") }
    var supportedContentTypes by remember { mutableStateOf<Set<String>>(setOf("LIVE_TV", "MOVIES", "TV_SHOWS")) }
    var showProviderPicker by remember { mutableStateOf(false) }
    var allProviders by remember { mutableStateOf<List<org.njarasoa.fijerena.core.network.provider.ProviderEntity>>(emptyList()) }
    var activeProviderId by remember { mutableStateOf(0L) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Load active provider info from Room
    LaunchedEffect(refreshTrigger) {
        withContext(Dispatchers.IO) {
            val providerRepo = ProviderRepository(context.applicationContext)
            allProviders = providerRepo.getAllProvidersList()
            val activeProvider = providerRepo.getActiveProvider()
            if (activeProvider != null) {
                providerName = activeProvider.name
                providerType = activeProvider.type
                activeProviderId = activeProvider.id
                val password = providerRepo.getPassword(activeProvider.id) ?: ""
                val mediaProvider = MediaProviderFactory.create(activeProvider, context.applicationContext, password)
                supportedContentTypes = mediaProvider.capabilities.supportedContentTypes
            } else {
                providerName = appSettings.providerName
            }
        }
    }

    // 5% padding for TV overscan safety
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = Spacing.tvSafeMarginHorizontal,
                vertical = Spacing.tvSafeMarginVertical
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
                val displayName = if (appSettings.isDevMode && providerType.isNotEmpty()) {
                    "$providerName ($providerType)"
                } else providerName
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = CinemaAccentLight,
                    modifier = Modifier.clickable { showProviderPicker = true }
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
                    if ("LIVE_TV" in supportedContentTypes) {
                        ContentTypeButton(
                            text = "📺 ${ContentType.LIVE_TV.displayName}",
                            onClick = { onContentTypeSelected(ContentType.LIVE_TV) }
                        )
                    }

                    if ("MOVIES" in supportedContentTypes) {
                        ContentTypeButton(
                            text = "🎬 ${ContentType.MOVIES.displayName}",
                            onClick = { onContentTypeSelected(ContentType.MOVIES) }
                        )
                    }

                    if ("TV_SHOWS" in supportedContentTypes) {
                        ContentTypeButton(
                            text = "📺 ${ContentType.TV_SHOWS.displayName}",
                            onClick = { onContentTypeSelected(ContentType.TV_SHOWS) }
                        )
                    }
                }
            }
        }

        // Provider picker dialog
        if (showProviderPicker && allProviders.size > 1) {
            val coroutineScope = rememberCoroutineScope()
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showProviderPicker = false },
                title = { androidx.compose.material3.Text("Switch Provider") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allProviders.forEach { provider ->
                            val isActive = provider.id == activeProviderId
                            val label = if (appSettings.isDevMode) {
                                "${provider.name} (${provider.type})"
                            } else provider.name
                            androidx.compose.material3.Surface(
                                onClick = {
                                    if (!isActive) {
                                        coroutineScope.launch {
                                            val providerRepo = ProviderRepository(context.applicationContext)
                                            providerRepo.setActiveProvider(provider.id)
                                            showProviderPicker = false
                                            refreshTrigger++
                                            onProviderChanged()
                                        }
                                    } else {
                                        showProviderPicker = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isActive)
                                    CinemaAccent.copy(alpha = 0.2f)
                                else
                                    CinemaSurfaceVariant,
                                shape = RoundedCornerShape(CinemaCornerRadius.small)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Text(
                                        text = label,
                                        color = if (isActive) CinemaAccent else CinemaTextPrimary
                                    )
                                    if (isActive) {
                                        androidx.compose.material3.Text(
                                            text = "Active",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CinemaAccent
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showProviderPicker = false }) {
                        androidx.compose.material3.Text("Close")
                    }
                }
            )
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
            .width(TvDimensions.formFieldWidth)
            .height(TvDimensions.buttonHeight),
        colors = ButtonDefaults.colors(
            containerColor = CinemaSurfaceVariant,
            contentColor = CinemaTextPrimary,
            focusedContainerColor = CinemaSurface,
            focusedContentColor = CinemaTextPrimary
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = TvFocusTokens.focusBorderWidth, color = CinemaAccentLight)
            )
        ),
        scale = ButtonDefaults.scale(
            scale = TvFocusTokens.defaultScale,
            focusedScale = TvFocusTokens.focusedScale,
            pressedScale = TvFocusTokens.pressedScale
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
    }
}
