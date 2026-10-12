package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.theme.CinemaCornerRadius
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.utils.SubscriptionExpiry
import org.njarasoa.fijerena.core.ui.utils.expiringSources
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

/**
 * A slim red strip under a catalogue page's header while one of the profile's Xtream sources
 * expires in 7 days or fewer, or has expired: one line per source. Information only, never a
 * focus stop, so the D-pad never lands on it.
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
                    .background(CinemaError, RoundedCornerShape(CinemaCornerRadius.small))
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = CinemaIcons.Warning,
                contentDescription = null,
                tint = CinemaTextPrimary,
                modifier = Modifier.size(TvDimensions.iconSmall),
            )
            Column {
                expiring.forEach { source ->
                    Text(
                        text = SubscriptionExpiry.bannerText(context.resources, source),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CinemaTextPrimary,
                    )
                }
            }
        }
    }
}
