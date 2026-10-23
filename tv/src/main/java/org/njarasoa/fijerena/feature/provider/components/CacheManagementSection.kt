@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package org.njarasoa.fijerena.feature.provider.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaAccent
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaError
import org.njarasoa.fijerena.core.ui.theme.CinemaTextPrimary
import org.njarasoa.fijerena.core.ui.theme.CinemaTextSecondary
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.utils.NumberUtils
import org.njarasoa.fijerena.core.ui.viewmodels.SyncState
import org.njarasoa.fijerena.feature.settings.components.SettingsRow
import org.njarasoa.fijerena.ui.components.buttons.CinemaDangerButton
import org.njarasoa.fijerena.ui.components.buttons.CinemaPrimaryButton
import org.njarasoa.fijerena.ui.components.input.PaneFocusState
import org.njarasoa.fijerena.ui.components.input.paneItem
import org.njarasoa.fijerena.ui.components.input.requestFocusWithRetry
import org.njarasoa.fijerena.ui.theme.LocalUiScale
import org.njarasoa.fijerena.ui.theme.Spacing
import org.njarasoa.fijerena.ui.theme.TvFocusTokens
import org.njarasoa.fijerena.ui.theme.scaled
import org.njarasoa.fijerena.ui.theme.CornerRadius as CinemaCornerRadius

/**
 * Library data: Sync now (Xtream), the last sync, totals and per-type counts. Clearing lives in
 * [ProviderDangerZoneSection].
 */
@Composable
fun CacheManagementSection(
    cacheStats: XtreamRepository.CacheStats?,
    pane: PaneFocusState,
    syncState: SyncState = SyncState.Idle,
    isXtream: Boolean = false,
    lastSyncedAtMs: Long = 0L,
    lastSyncDurationMs: Long = 0L,
    lastSyncError: String? = null,
    lastSyncInserted: Int = 0,
    lastSyncUpdated: Int = 0,
    lastSyncDeleted: Int = 0,
    onSyncClick: () -> Unit = {},
) {
    val scale = LocalUiScale.current
    ProviderSectionTitle(
        title = stringResource(R.string.provider_section_library_data),
        subtitle = stringResource(R.string.provider_data_management_desc),
    )
    Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

    cacheStats?.let { stats ->
        if (isXtream) {
            CinemaPrimaryButton(
                onClick = onSyncClick,
                text =
                    if (syncState is SyncState.Syncing) {
                        stringResource(R.string.provider_syncing)
                    } else {
                        stringResource(R.string.provider_sync_now_button)
                    },
                enabled = syncState !is SyncState.Syncing,
                modifier = Modifier.paneItem(pane, "sync"),
            )

            if (lastSyncedAtMs > 0L) {
                Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                val context = androidx.compose.ui.platform.LocalContext.current
                val time = NumberUtils.formatTimestamp(context, lastSyncedAtMs)
                val duration = NumberUtils.formatDuration(lastSyncDurationMs)
                CinemaText(
                    stringResource(R.string.provider_last_sync_status, time, duration),
                    role = TextRole.BodySmall,
                    tone = TextTone.Secondary,
                )

                // Delta only means something for Xtream — every other provider type has no
                // equivalent row-level diff, and its columns just hold whatever they defaulted
                // to. Only shown next to a clean sync: on error the columns hold the last
                // *successful* sync's delta (ProviderDao.updateSyncStats COALESCEs rather than
                // zeroing on failure), so showing it next to an error would misleadingly read as
                // "this failed run found 5 changes".
                if (isXtream && lastSyncError == null) {
                    CinemaText(
                        if (lastSyncInserted == 0 && lastSyncUpdated == 0 && lastSyncDeleted == 0) {
                            stringResource(R.string.provider_sync_no_changes)
                        } else {
                            stringResource(R.string.provider_sync_delta, lastSyncInserted, lastSyncUpdated, lastSyncDeleted)
                        },
                        role = TextRole.BodySmall,
                        tone = TextTone.Secondary,
                    )
                }
            }

            if (syncState is SyncState.Error || (syncState is SyncState.Idle && lastSyncError != null)) {
                val errorMsg = ((syncState as? SyncState.Error)?.message ?: lastSyncError)?.let(Redact::text)
                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                    CinemaText(
                        errorMsg,
                        role = TextRole.BodySmall,
                        tone = TextTone.Danger,
                    )
                }
            }
            if (syncState is SyncState.Success) {
                Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
                CinemaText(
                    stringResource(R.string.provider_sync_success),
                    role = TextRole.BodySmall,
                    tone = TextTone.Accent,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
        }

        val totalItems = stats.liveTv.itemsCount + stats.movies.itemsCount + stats.tvShows.itemsCount

        CinemaText(
            stringResource(R.string.provider_total_db_items_label),
            role = TextRole.Body,
            tone = TextTone.Primary,
        )
        CinemaText(
            stringResource(R.string.provider_total_items_value, NumberUtils.formatCount(totalItems)),
            role = TextRole.Title,
            tone = TextTone.Accent,
        )

        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        // A type the source has nothing of is left out.
        val types =
            listOf(
                (stats.liveTv.itemsCount > 0) to
                    Pair(
                        R.string.provider_live_tv_label,
                        stringResource(
                            R.string.provider_live_tv_stats,
                            NumberUtils.formatCount(stats.liveTv.categoryCount),
                            NumberUtils.formatCount(stats.liveTv.itemsCount),
                        ),
                    ),
                (stats.movies.itemsCount > 0) to
                    Pair(
                        R.string.provider_movies_label,
                        stringResource(
                            R.string.provider_movies_stats,
                            NumberUtils.formatCount(stats.movies.categoryCount),
                            NumberUtils.formatCount(stats.movies.itemsCount),
                        ),
                    ),
                (stats.tvShows.itemsCount > 0) to
                    Pair(
                        R.string.provider_tv_shows_label,
                        stringResource(
                            R.string.provider_tv_shows_stats,
                            NumberUtils.formatCount(stats.tvShows.categoryCount),
                            NumberUtils.formatCount(stats.tvShows.itemsCount),
                            NumberUtils.formatCount(stats.tvShows.episodesCount),
                        ),
                    ),
            ).filter { it.first }.map { it.second }
        types.forEachIndexed { index, (label, value) ->
            if (index > 0) Spacer(modifier = Modifier.height(Spacing.sm.scaled(scale)))
            CountRow(label = stringResource(label), value = value)
        }

        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))

        CinemaText(
            stringResource(R.string.provider_epg_stats, NumberUtils.formatCount(stats.epgCount)),
            role = TextRole.BodySmall,
            tone = TextTone.Secondary,
        )
    }
}

@Composable
private fun CountRow(
    label: String,
    value: String,
) {
    Column {
        CinemaText(text = label, role = TextRole.Heading, tone = TextTone.Primary)
        CinemaText(text = value, role = TextRole.Body, tone = TextTone.Accent)
    }
}

/**
 * Danger zone's one row in Edit Source's settings column: its actions sit on their own page
 * ([ProviderDangerZonePane]), out of reach of a stray OK.
 */
@Composable
fun ProviderDangerZoneRow(
    pane: PaneFocusState,
    onOpen: () -> Unit,
    focusRequester: FocusRequester,
) {
    SettingsRow(
        title = stringResource(R.string.provider_section_danger_zone),
        description = stringResource(R.string.provider_danger_zone_summary),
        onClick = onOpen,
        modifier = Modifier.paneItem(pane, "danger_zone"),
        focusRequester = focusRequester,
    )
}

/**
 * Danger zone's page, in place of the settings column: every destructive action of this source,
 * each opening its confirm. Focus opens on the first; Back or Left closes it ([onClose]).
 */
@Composable
fun ProviderDangerZonePane(
    cacheStats: XtreamRepository.CacheStats?,
    onClose: () -> Unit,
    onClearFavoritesClick: () -> Unit,
    onClearProgressClick: () -> Unit,
    onClearAllCacheClick: () -> Unit,
    onClearLiveTvClick: () -> Unit,
    onClearMoviesClick: () -> Unit,
    onClearTvShowsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalUiScale.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocusWithRetry() }
    BackHandler(onBack = onClose)

    @Composable
    fun DangerAction(
        description: String?,
        buttonText: String,
        key: String,
        onClick: () -> Unit,
    ) {
        Spacer(modifier = Modifier.height(Spacing.md.scaled(scale)))
        description?.let {
            CinemaText(text = it, role = TextRole.BodySmall, tone = TextTone.Secondary)
            Spacer(modifier = Modifier.height(Spacing.xs.scaled(scale)))
        }
        ProviderDangerButton(
            onClick = onClick,
            text = buttonText,
            modifier = if (key == "clear_favorites") Modifier.focusRequester(firstFocus) else Modifier,
        )
    }

    Column(
        modifier =
            modifier.verticalScroll(rememberScrollState()).onPreviewKeyEvent { event ->
                val leaves = event.key == Key.Back || event.key == Key.DirectionLeft
                if (leaves && event.type == KeyEventType.KeyUp) onClose()
                leaves
            },
    ) {
        ProviderSectionTitle(
            title = stringResource(R.string.provider_section_danger_zone),
            titleTone = TextTone.Danger,
        )
        DangerAction(
            description = stringResource(R.string.provider_clear_favorites_desc),
            buttonText = stringResource(R.string.provider_clear_favorites_button),
            key = "clear_favorites",
            onClick = onClearFavoritesClick,
        )
        DangerAction(
            description = stringResource(R.string.provider_clear_progress_desc),
            buttonText = stringResource(R.string.provider_clear_progress_button),
            key = "clear_progress",
            onClick = onClearProgressClick,
        )
        cacheStats?.let { stats ->
            DangerAction(
                description = stringResource(R.string.provider_clear_library_desc),
                buttonText = stringResource(R.string.provider_clear_cached_library_button),
                key = "clear_cache",
                onClick = onClearAllCacheClick,
            )
            // Per-type clears: only for types that have something to clear.
            val clearLabel = stringResource(R.string.provider_clear_button)
            if (stats.liveTv.itemsCount > 0) {
                DangerAction(
                    description = stringResource(R.string.provider_clear_type_format, stringResource(R.string.provider_live_tv_label)),
                    buttonText = clearLabel,
                    key = "clear_live",
                    onClick = onClearLiveTvClick,
                )
            }
            if (stats.movies.itemsCount > 0) {
                DangerAction(
                    description = stringResource(R.string.provider_clear_type_format, stringResource(R.string.provider_movies_label)),
                    buttonText = clearLabel,
                    key = "clear_movies",
                    onClick = onClearMoviesClick,
                )
            }
            if (stats.tvShows.itemsCount > 0) {
                DangerAction(
                    description = stringResource(R.string.provider_clear_type_format, stringResource(R.string.provider_tv_shows_label)),
                    buttonText = clearLabel,
                    key = "clear_tv_shows",
                    onClick = onClearTvShowsClick,
                )
            }
        }
    }
}

/** Wipes something of the source ([CinemaDangerButton]). */
@Composable
internal fun ProviderDangerButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
) = CinemaDangerButton(onClick = onClick, text = text, modifier = modifier)
