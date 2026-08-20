@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.ui.components.input

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.ListItem

/**
 * `androidx.tv.material3.ListItem` pre-wired to [TvInputDefaults]. Every control in this package
 * routes through here so focus and selection look identical wherever they appear.
 */
@Composable
internal fun TvInputListItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: (@Composable BoxScope.() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    headlineContent: @Composable () -> Unit,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        supportingContent = supportingContent,
        shape = TvInputDefaults.shape(),
        colors = TvInputDefaults.colors(),
        scale = TvInputDefaults.scale(),
        border = TvInputDefaults.border(),
        glow = TvInputDefaults.glow(),
        headlineContent = headlineContent,
    )
}
