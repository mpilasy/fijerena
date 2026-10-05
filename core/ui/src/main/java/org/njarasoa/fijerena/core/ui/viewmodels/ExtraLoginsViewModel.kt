package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.friendlyErrorMessage
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.SourceLogins
import org.njarasoa.fijerena.core.network.xtream.XtreamLoginCheck
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

/**
 * Edit Source → Logins for an Xtream source: its main login and the extra ones playback shares,
 * each with what the panel says about it (active, expiry). Add runs the same-panel check first.
 * See docs/plans/20261005_shared-logins-plan.md → Phase 1.
 *
 * [context] is the application context ([Factory] passes nothing else).
 */
class ExtraLoginsViewModel(
    private val context: Context,
    private val providerId: Long,
    private val providerRepository: ProviderRepository = ProviderRepository(context),
) : ViewModel() {
    /** What the panel said about a login; [Unknown] when the check failed or hasn't run. */
    sealed interface LoginState {
        data object Loading : LoginState

        data class Known(
            val status: XtreamLoginCheck.Status,
        ) : LoginState

        data object Unknown : LoginState
    }

    /** [hasPassword] false: an extra login whose password this device doesn't have (an import). */
    data class LoginRow(
        val username: String,
        val isMain: Boolean,
        val hasPassword: Boolean,
        val state: LoginState,
    )

    private val _rows = MutableStateFlow<List<LoginRow>>(emptyList())
    val rows: StateFlow<List<LoginRow>> = _rows.asStateFlow()

    /** True while adding (the same-panel check takes a few seconds) or saving. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** A result to show once (added, refused, …); [clearMessage] after showing it. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var entity: ProviderEntity? = null
    private var logins: SourceLogins? = null

    init {
        refresh()
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Reloads the logins and asks the panel about each, in parallel. */
    fun refresh() {
        viewModelScope.launchGuarded("ExtraLoginsViewModel.refresh", onError = ::failed) { load() }
    }

    /** The logins and what the panel says about each; done before an action clears [busy]. */
    private suspend fun load() {
        coroutineScope {
            val source = providerRepository.getProviderById(providerId)
            entity = source
            val current = source?.let { providerRepository.getSourceLogins(it) }
            logins = current
            if (source != null && current != null) {
                _rows.value = rowsOf(current) { LoginState.Loading }
                val states =
                    current.all
                        .map { login ->
                            async {
                                if (login.password.isEmpty()) {
                                    LoginState.Unknown
                                } else {
                                    XtreamLoginCheck
                                        .status(source.url, login)
                                        .fold({ LoginState.Known(it) }, { LoginState.Unknown })
                                }
                            }
                        }.awaitAll()
                val byUser =
                    current.all
                        .map { it.username }
                        .zip(states)
                        .toMap()
                _rows.value = rowsOf(current) { byUser[it] ?: LoginState.Unknown }
            }
        }
    }

    /**
     * The username that becomes the main login if [username] is removed, or null when [username]
     * isn't the main one — for the confirmation ("Remove this login? *user2* becomes the main login.").
     */
    fun promotedOnRemove(username: String): String? =
        logins
            ?.takeIf { it.main.username == username }
            ?.let { (it.remove(username) as? SourceLogins.Change.Done)?.logins?.main?.username }

    fun add(
        username: String,
        password: String,
    ) {
        val source = entity
        val current = logins
        if (source == null || current == null || _busy.value) return
        val change = current.add(username, password)
        if (change !is SourceLogins.Change.Done) {
            _message.value = messageFor(change)
            return
        }
        _busy.value = true
        viewModelScope.launchGuarded("ExtraLoginsViewModel.add", onError = ::failed) {
            val extra = change.logins.extras.last()
            val check = XtreamLoginCheck.samePanel(source.url, current.main, extra)
            val text =
                when (check) {
                    is XtreamLoginCheck.SamePanel.Ok -> {
                        providerRepository.saveSourceLogins(providerId, change.logins)
                        context.getString(R.string.provider_logins_added_format, extra.username)
                    }

                    XtreamLoginCheck.SamePanel.Refused -> {
                        context.getString(R.string.provider_logins_refused)
                    }

                    XtreamLoginCheck.SamePanel.DifferentCatalogue -> {
                        context.getString(R.string.provider_logins_different_server)
                    }

                    is XtreamLoginCheck.SamePanel.Failed -> {
                        friendlyErrorMessage(check.error, context, AppSettings(context).isDevMode)
                    }
                }
            load()
            _message.value = text
            _busy.value = false
        }
    }

    /** Removes [username]; removing the main login promotes the first extra one. */
    fun remove(username: String) = change { current -> current.remove(username) }

    /** Makes [username] the main login; the old main one becomes an extra login. */
    fun makeMain(username: String) = change { current -> current.makeMain(username) }

    private fun change(edit: (SourceLogins) -> SourceLogins.Change) {
        val current = logins
        if (current == null || _busy.value) return
        val result = edit(current)
        if (result is SourceLogins.Change.Done) {
            _busy.value = true
            viewModelScope.launchGuarded("ExtraLoginsViewModel.change", onError = ::failed) {
                providerRepository.saveSourceLogins(providerId, result.logins)
                load()
                _busy.value = false
            }
        } else {
            _message.value = messageFor(result)
        }
    }

    private fun failed(e: Throwable) {
        _busy.value = false
        _message.value = friendlyErrorMessage(e, context, AppSettings(context).isDevMode)
    }

    private fun messageFor(change: SourceLogins.Change): String? =
        when (change) {
            SourceLogins.Change.DuplicateUsername -> context.getString(R.string.provider_logins_duplicate)
            SourceLogins.Change.LastLogin -> context.getString(R.string.provider_logins_last)
            SourceLogins.Change.UnknownUsername -> context.getString(R.string.provider_logins_username_required)
            SourceLogins.Change.PasswordNeeded -> context.getString(R.string.provider_logins_password_needed_main)
            is SourceLogins.Change.Done -> null
        }

    private fun rowsOf(
        logins: SourceLogins,
        state: (String) -> LoginState,
    ): List<LoginRow> =
        logins.all.mapIndexed { index, login ->
            LoginRow(login.username, isMain = index == 0, hasPassword = login.password.isNotEmpty(), state = state(login.username))
        }

    companion object {
        /**
         * The status line under a login, the same on TV and mobile: "Main login · Expires 2027-02-01",
         * "Expired", "Password needed", "Checking…".
         */
        fun statusText(
            context: Context,
            row: LoginRow,
        ): String {
            val status = (row.state as? LoginState.Known)?.status
            val expiresAtSec = status?.expiresAtSec
            val state =
                when {
                    !row.hasPassword -> {
                        context.getString(R.string.provider_logins_password_needed)
                    }

                    row.state is LoginState.Loading -> {
                        context.getString(R.string.provider_logins_checking)
                    }

                    status == null -> {
                        context.getString(R.string.provider_logins_unknown)
                    }

                    !status.active -> {
                        context.getString(R.string.provider_logins_inactive_format, status.statusText)
                    }

                    expiresAtSec != null -> {
                        val date =
                            java.time.Instant
                                .ofEpochSecond(expiresAtSec)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                                .toString()
                        context.getString(R.string.provider_logins_expires_format, date)
                    }

                    else -> {
                        context.getString(R.string.provider_logins_no_expiry)
                    }
                }
            return if (row.isMain) context.getString(R.string.provider_logins_main_format, state) else state
        }
    }

    class Factory(
        context: Context,
        private val providerId: Long,
    ) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ExtraLoginsViewModel(appContext, providerId) as T
    }
}
