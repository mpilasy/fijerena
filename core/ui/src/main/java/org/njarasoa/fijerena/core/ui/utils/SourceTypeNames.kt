package org.njarasoa.fijerena.core.ui.utils

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.R

/** The kind of source's name in the app's language ("Remote M3U", "M3U distant"), for the UI. */
@get:StringRes
val ProviderType.nameRes: Int
    get() =
        when (this) {
            ProviderType.XTREAM -> R.string.source_type_xtream
            ProviderType.LOCAL -> R.string.source_type_local
            ProviderType.SMB -> R.string.source_type_smb
            ProviderType.JELLYFIN -> R.string.source_type_jellyfin
            ProviderType.REMOTE_M3U -> R.string.source_type_remote_m3u
        }

/** A stored source type ("REMOTE_M3U") by its name in the app's language; an unknown one as it is. */
@Composable
fun sourceTypeName(type: String): String = ProviderType.entries.firstOrNull { it.name == type }?.let { stringResource(it.nameRes) } ?: type
