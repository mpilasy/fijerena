package org.njarasoa.fijerena.feature.category.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.SkeletonList
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvDimensions

@Composable
internal fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.common_loading),
            style = MaterialTheme.typography.titleMedium,
            color = CinemaTextSecondary,
        )
        SkeletonList(
            rowCount = 6,
            rowHeight = TvDimensions.cardHeight,
            thumbnailWidth = TvDimensions.posterWidth * 0.5f,
            thumbnailHeight = TvDimensions.posterHeight * 0.5f,
            verticalSpacing = Spacing.sm,
        )
    }
}
