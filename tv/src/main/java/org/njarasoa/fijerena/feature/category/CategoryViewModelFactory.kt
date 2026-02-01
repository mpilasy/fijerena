package org.njarasoa.fijerena.feature.category

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.XtreamRepository

/**
 * Factory for creating CategoryViewModel with XtreamRepository dependency.
 */
class CategoryViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CategoryViewModel::class.java)) {
            val accountManager = AccountManager(context.applicationContext)
            val repository = XtreamRepository(accountManager)
            return CategoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
