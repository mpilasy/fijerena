package org.njarasoa.fijerena.feature.contentselection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.ui.theme.CinemaAccent
import org.njarasoa.fijerena.ui.theme.CinemaAccentDark
import org.njarasoa.fijerena.ui.theme.CinemaAccentLight
import org.njarasoa.fijerena.ui.theme.CinemaBackground
import org.njarasoa.fijerena.ui.theme.CinemaOrange
import org.njarasoa.fijerena.ui.theme.CinemaOrangeDark
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.ui.theme.MobileDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileContentTypeSelectionScreen(
    onContentTypeSelected: (contentType: String) -> Unit,
    onSettings: () -> Unit = {},
    onProviderChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val appSettings = remember { AppSettings(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    var providerName by remember { mutableStateOf("") }
    var providerType by remember { mutableStateOf("") }
    var supportedContentTypes by remember { mutableStateOf<Set<String>>(setOf("LIVE_TV", "MOVIES", "TV_SHOWS")) }
    var showProviderPicker by remember { mutableStateOf(false) }
    var allProviders by remember { mutableStateOf<List<ProviderEntity>>(emptyList()) }
    var activeProviderId by remember { mutableStateOf(0L) }
    var refreshTrigger by remember { mutableStateOf(0) }

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

    Scaffold(
        topBar = {
            val displayName = buildString {
                append(providerName.ifEmpty { "Fijerena" })
                if (appSettings.isDevMode && providerType.isNotEmpty()) {
                    append(" ($providerType)")
                }
            }
            TopAppBar(
                title = {
                    Text(
                        text = displayName,
                        modifier = Modifier.clickable { showProviderPicker = true },
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(CinemaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(CinemaSpacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select Content Type",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = CinemaSpacing.lg)
            )

            if ("LIVE_TV" in supportedContentTypes) {
                GradientContentCard(
                    title = "Live TV",
                    description = "Watch live television channels",
                    gradientColors = listOf(CinemaOrange, CinemaOrangeDark),
                    onClick = { onContentTypeSelected("LIVE_TV") }
                )
            }

            if ("MOVIES" in supportedContentTypes) {
                GradientContentCard(
                    title = "Movies",
                    description = "Browse on-demand movies",
                    gradientColors = listOf(CinemaAccent, CinemaAccentDark),
                    onClick = { onContentTypeSelected("MOVIES") }
                )
            }

            if ("TV_SHOWS" in supportedContentTypes) {
                GradientContentCard(
                    title = "TV Shows",
                    description = "Watch series and episodes",
                    gradientColors = listOf(CinemaAccentLight, CinemaAccent),
                    onClick = { onContentTypeSelected("TV_SHOWS") }
                )
            }
        }
    }

    // Provider picker dialog
    if (showProviderPicker && allProviders.size > 1) {
        AlertDialog(
            onDismissRequest = { showProviderPicker = false },
            title = { Text("Switch Provider") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(CinemaSpacing.xs)
                ) {
                    allProviders.forEach { provider ->
                        val isActive = provider.id == activeProviderId
                        val label = if (appSettings.isDevMode) {
                            "${provider.name} (${provider.type})"
                        } else provider.name
                        Surface(
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
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(CinemaCornerRadius.small)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(CinemaSpacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isActive) {
                                    Text(
                                        text = "Active",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderPicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}

/**
 * Full-width gradient card for content type selection (mobile).
 */
@Composable
private fun GradientContentCard(
    title: String,
    description: String,
    gradientColors: List<androidx.compose.ui.graphics.Color>,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(MobileDimensions.contentTypeCardHeight),
        shape = RoundedCornerShape(CinemaCornerRadius.large)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(colors = gradientColors),
                    shape = RoundedCornerShape(CinemaCornerRadius.large)
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                modifier = Modifier.padding(CinemaSpacing.lg)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = CinemaTextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CinemaTextPrimary.copy(alpha = CinemaAlpha.textMedium)
                )
            }
        }
    }
}
