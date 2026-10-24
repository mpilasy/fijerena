package org.njarasoa.fijerena.feature.category

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.utils.SubscriptionExpiry
import org.njarasoa.fijerena.core.ui.utils.expiringSources
import org.njarasoa.fijerena.ui.theme.MobileDimensions

/**
 * A slim red strip under a catalogue page's header while one of the profile's Xtream sources
 * expires in 7 days or fewer, or has expired: one line per source.
 */
@Composable
internal fun SourceExpiryBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val flow = remember { expiringSources(context) }
    val expiring by flow.collectAsStateWithLifecycle(emptyList())
    if (expiring.isNotEmpty()) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .background(CinemaTheme.colors.danger)
                    .padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CinemaSpacing.xs),
        ) {
            Icon(
                imageVector = CinemaIcons.Warning,
                contentDescription = null,
                tint = CinemaTheme.colors.onDanger,
                modifier = Modifier.size(MobileDimensions.iconSmall),
            )
            Column {
                expiring.forEach { source ->
                    CinemaText(
                        text = SubscriptionExpiry.bannerText(context.resources, source),
                        role = TextRole.BodySmall,
                        tone = TextTone.OnDanger,
                    )
                }
            }
        }
    }
}
