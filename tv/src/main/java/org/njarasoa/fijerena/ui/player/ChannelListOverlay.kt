package org.njarasoa.fijerena.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.ui.theme.CinemaSurfaceVariant
import org.njarasoa.fijerena.ui.theme.Spacing

@Composable
fun ChannelListOverlay(
    title: String,
    streams: List<MediaItem>,
    currentStreamTitle: String,
    onStreamSelected: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
    alignment: Alignment = Alignment.CenterEnd
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    // Request focus when overlay appears
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Handle back button
    DisposableEffect(Unit) {
        val callback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onDismiss()
            }
        }
        val activity = context as? androidx.activity.ComponentActivity
        activity?.onBackPressedDispatcher?.addCallback(callback)

        onDispose {
            callback.remove()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = CinemaAlpha.overlayHeavy))
            .focusable(), // Capture focus to prevent underlying player controls from receiving events
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .width(400.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(Spacing.lg)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                if (streams.isEmpty()) {
                    Text(
                        text = "No streams available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = CinemaAlpha.textMedium)
                    )
                } else {
                    TvLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(streams.size) { index ->
                            val stream = streams[index]
                            val isSelected = stream.name == currentStreamTitle

                            Button(
                                onClick = { onStreamSelected(stream) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier),
                                colors = androidx.tv.material3.ButtonDefaults.colors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = CinemaAlpha.tint)
                                    else CinemaSurfaceVariant,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = stream.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
