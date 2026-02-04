package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.XtreamRepository

/**
 * Factory for creating SearchViewModel with required dependencies
 */
class SearchViewModelFactory(
    private val context: Context,
    private val contentType: String,
    private val providerId: Long = 0L
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            val accountManager = AccountManager(context.applicationContext)
            val repository = XtreamRepository(accountManager, context.applicationContext, providerId)
            return SearchViewModel(repository, contentType) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
