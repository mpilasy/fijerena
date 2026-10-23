package org.njarasoa.fijerena.core.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.SourceShortNames
import org.njarasoa.fijerena.core.ui.di.AppContainer

/** Whether source badges show: only while the profile in use has Expert mode (developer mode) on. */
val LocalShowSourceBadges = compositionLocalOf { false }

/**
 * The sources' badge short names ("X4P", [SourceShortNames]): by source id, and by name for the
 * callers that only have the name (the first source wins when two share one).
 */
data class SourceShortNameMap(
    val byId: Map<Long, String> = emptyMap(),
    val byName: Map<String, String> = emptyMap(),
)

val LocalSourceShortNames = compositionLocalOf { SourceShortNameMap() }

/**
 * Provides [LocalShowSourceBadges] and [LocalSourceShortNames] to [content], following profile
 * switches, the profile's Expert mode and every source's name and short name.
 */
@Composable
fun ProvideSourceBadges(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val show by remember(context) { AppSettings(context).observeDevMode().map { (_, on) -> on } }.collectAsState(initial = false)
    val shortNames by remember(context) {
        val repository = AppContainer.getInstance(context).providerRepository
        combine(repository.getAllProviders(), repository.sourceShortNames()) { sources, byId ->
            SourceShortNameMap(
                byId = byId,
                byName =
                    sources
                        .sortedBy { it.id }
                        .reversed()
                        .mapNotNull { s -> byId[s.id]?.let { s.name to it } }
                        .toMap(),
            )
        }
    }.collectAsState(initial = SourceShortNameMap())
    CompositionLocalProvider(
        LocalShowSourceBadges provides show,
        LocalSourceShortNames provides shortNames,
        content = content,
    )
}

/** [providerId]'s name, once read (null before, and for a source this device doesn't have). */
@Composable
fun rememberSourceName(providerId: Long): String? {
    val context = LocalContext.current.applicationContext
    val name by produceState<String?>(null, providerId) {
        value =
            AppContainer
                .getInstance(context)
                .providerRepository
                .getProviderById(providerId)
                ?.name
    }
    return name
}
