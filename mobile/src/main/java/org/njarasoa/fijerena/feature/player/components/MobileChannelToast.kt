package org.njarasoa.fijerena.feature.player.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.player.model.EpgProgram
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.GlassPanel
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.ui.theme.CinemaTextPrimary

@Composable
fun ChannelToast(
    channelName: String,
    currentEpgProgram: EpgProgram? = null,
) {
    GlassPanel(
        modifier =
            Modifier
                .statusBarsPadding()
                .padding(top = CinemaSpacing.xl),
    ) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = CinemaSpacing.lg,
                    vertical = CinemaSpacing.sm,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CinemaText(
                text = stringResource(R.string.player_now_playing),
                role = TextRole.Caption,
                tone = TextTone.Accent,
            )
            CinemaText(
                // The clean name: "CP24 ◉", not "CA - CP24 HD ◉".
                text = parseDisplayTitle(channelName).title.ifBlank { channelName },
                role = TextRole.Heading,
                textAlign = TextAlign.Center,
            )
            if (currentEpgProgram != null) {
                CinemaText(
                    text = currentEpgProgram.title,
                    textAlign = TextAlign.Center,
                    role = TextRole.BodySmall,
                    tone = TextTone.Secondary,
                )
            }
        }
    }
}
