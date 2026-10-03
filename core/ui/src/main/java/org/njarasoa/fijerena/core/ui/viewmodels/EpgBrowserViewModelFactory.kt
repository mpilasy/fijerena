package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.njarasoa.fijerena.core.ui.di.AppContainer

class EpgBrowserViewModelFactory(
    context: Context,
    /** The TV Guide list the browser was opened from (GD5); null for the whole guide. */
    private val categoryId: String? = null,
) : ViewModelProvider.Factory {
    // Store only the application context, not the raw parameter — see CategoryViewModelFactory.
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EpgBrowserViewModel::class.java)) {
            val container = AppContainer.getInstance(appContext)
            return EpgBrowserViewModel(appContext, container.providerRepository, categoryId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
