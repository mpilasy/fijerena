package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.XtreamRepository

/**
 * Factory for creating LoginViewModel with XtreamRepository dependency.
 *
 * Creates the repository with AccountManager for encrypted credential storage.
 *
 * For dependency injection (Hilt/Koin), use @HiltViewModel instead.
 */
class LoginViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            val accountManager = AccountManager(context.applicationContext)
            val repository = XtreamRepository(accountManager, context.applicationContext)
            return LoginViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
