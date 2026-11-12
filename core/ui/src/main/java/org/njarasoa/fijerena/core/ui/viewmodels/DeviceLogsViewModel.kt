package org.njarasoa.fijerena.core.ui.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.sync.DeviceBuild
import org.njarasoa.fijerena.core.network.sync.DeviceReport

/**
 * Live Sync → a device's Logs (the device diagnostics sync plan): the report another device of the
 * group sent, with its name and build, kept up to date while the page is open.
 */
class DeviceLogsViewModel(
    app: Application,
    deviceId: String,
    /** The name the devices list showed, for a device the group never renamed. */
    fallbackName: String,
) : ViewModel() {
    data class State(
        val name: String,
        val build: DeviceBuild? = null,
        /** Null when the device has sent none. */
        val report: DeviceReport? = null,
    )

    private val settings = AppSettings(app)

    val state: StateFlow<State> =
        combine(settings.observeDeviceReports(), settings.observeDeviceBuilds(), settings.observeDeviceNames()) { reports, builds, names ->
            State(name = names[deviceId] ?: fallbackName, build = builds[deviceId], report = reports[deviceId])
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), State(fallbackName))

    class Factory(
        private val app: Application,
        private val deviceId: String,
        private val fallbackName: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DeviceLogsViewModel(app, deviceId, fallbackName) as T
    }

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}
