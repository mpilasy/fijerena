package org.njarasoa.fijerena.feature.epg

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.XtreamRepository

class EpgViewModelFactory(
    private val context: Context,
    private val categoryId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EpgViewModel::class.java)) {
            val accountManager = AccountManager(context)
            val repository = XtreamRepository(accountManager, context)
            @Suppress("UNCHECKED_CAST")
            return EpgViewModel(repository, categoryId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
