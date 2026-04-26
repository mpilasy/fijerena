package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

import org.njarasoa.fijerena.core.ui.di.AppContainer

class EpgBrowserViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EpgBrowserViewModel::class.java)) {
            val container = AppContainer.getInstance(context.applicationContext)
            return EpgBrowserViewModel(context.applicationContext, container.providerRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
