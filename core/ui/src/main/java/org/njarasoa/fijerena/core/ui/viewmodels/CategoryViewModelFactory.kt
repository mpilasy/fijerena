package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CategoryViewModelFactory(
    context: Context,
    private val contentType: String,
    private val initialCategoryId: String? = null,
    private val providerId: Long = 0L,
) : ViewModelProvider.Factory {
    // Store only the application context: a `val` constructor property would keep the caller's
    // Context (often an Activity) for as long as this factory lives (e.g. remembered in Compose).
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CategoryViewModel::class.java)) {
            return CategoryViewModel(appContext, contentType, initialCategoryId, providerId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
