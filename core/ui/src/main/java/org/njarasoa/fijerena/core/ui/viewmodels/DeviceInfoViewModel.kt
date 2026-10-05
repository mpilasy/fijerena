package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.player.diagnostics.Redact
import org.njarasoa.fijerena.core.ui.deviceinfo.DeviceInfoSection
import org.njarasoa.fijerena.core.ui.deviceinfo.coreSections
import org.njarasoa.fijerena.core.ui.deviceinfo.mediaSections
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

/**
 * Settings → About → Device info: facts read once per open and on Refresh. [context] is the
 * application context; labels are resolved where they are shown. See
 * docs/plans/20261004_device-info-screen-plan.md.
 */
class DeviceInfoViewModel(
    private val context: Context,
    private val gitHash: String,
    private val buildTime: String,
) : ViewModel() {
    /** Null while loading. */
    private val _sections = MutableStateFlow<List<DeviceInfoSection>?>(null)
    val sections: StateFlow<List<DeviceInfoSection>?> = _sections.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launchGuarded("DeviceInfoViewModel.reload", onError = { _sections.value = emptyList() }) {
            _sections.value = withContext(Dispatchers.IO) { coreSections(context, gitHash, buildTime) + mediaSections(context) }
        }
    }

    companion object {
        /** Every section as plain text for sharing, in the language of [localized]; secrets masked ([Redact]). */
        fun asText(
            sections: List<DeviceInfoSection>,
            localized: Context,
        ): String =
            Redact.text(
                sections.joinToString("\n\n") { section ->
                    localized.getString(section.title) + "\n" +
                        section.rows.joinToString("\n") { row ->
                            "${row.label.asString(localized)}: ${row.value.asString(localized).replace("\n", ", ")}"
                        }
                },
            )
    }
}
