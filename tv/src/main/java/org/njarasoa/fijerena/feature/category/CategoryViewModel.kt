package org.njarasoa.fijerena.feature.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.njarasoa.fijerena.core.network.Result
import org.njarasoa.fijerena.core.network.XtreamRepository
import org.njarasoa.fijerena.core.player.model.XtreamCategory

/**
 * ViewModel for managing category data from Xtream API.
 *
 * Handles:
 * - Loading categories from XtreamRepository
 * - Loading, Success, and Error states
 * - Category selection tracking
 */
class CategoryViewModel(
    private val repository: XtreamRepository
) : ViewModel() {

    sealed class UiState {
        data object Loading : UiState()
        data class Success(val categories: List<XtreamCategory>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            // First, ensure we have an authenticated session
            if (!repository.isAuthenticated()) {
                when (val restoreResult = repository.restoreSession()) {
                    is Result.Error -> {
                        val errorMessage = "Session expired. Please login again."
                        _uiState.value = UiState.Error(errorMessage)
                        return@launch
                    }
                    is Result.Success -> {
                        // Session restored successfully, continue
                    }
                }
            }

            // Now fetch categories
            when (val result = repository.getCategories()) {
                is Result.Success -> {
                    _uiState.value = UiState.Success(result.data)
                }
                is Result.Error -> {
                    val errorMessage = result.message ?: "Failed to load categories"
                    _uiState.value = UiState.Error(errorMessage)
                }
            }
        }
    }

    fun onCategorySelected(categoryId: String) {
        _selectedCategoryId.value = categoryId
    }

    fun retry() {
        loadCategories()
    }
}
