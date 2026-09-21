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
    context: Context,
    private val providerId: Long = 0L,
) : ViewModelProvider.Factory {
    // Store only the application context, not the raw parameter — see CategoryViewModelFactory.
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            val accountManager = AccountManager(appContext, providerId)
            val repository = XtreamRepository(accountManager, appContext, providerId)
            return LoginViewModel(repository, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
