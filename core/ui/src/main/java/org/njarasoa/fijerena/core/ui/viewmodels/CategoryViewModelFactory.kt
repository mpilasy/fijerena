package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CategoryViewModelFactory(
    context: Context,
    private val contentType: String,
    private val initialCategoryId: String? = null,
) : ViewModelProvider.Factory {
    // Store only the application context, not the raw parameter: a `val` constructor property
    // would hold whatever Context the caller passed (typically an Activity) for as long as this
    // factory instance is retained (e.g. remember { }'d in Compose across recompositions).
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CategoryViewModel::class.java)) {
            return CategoryViewModel(appContext, contentType, initialCategoryId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
