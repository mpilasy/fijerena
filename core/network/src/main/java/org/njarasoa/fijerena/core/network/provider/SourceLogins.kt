package org.njarasoa.fijerena.core.network.provider

/**
 * An Xtream source's logins: the main one (`providers.username` + `provider_creds_<id>`), which
 * does catalogue sync, the guide and everything but playback, and the extra ones on the same panel
 * that playback shares with it. Changes are pure functions here; [ProviderRepository] stores the
 * result. See docs/plans/20261005_shared-logins-plan.md.
 */
data class SourceLogins(
    val main: ProviderRepository.Login,
    val extras: List<ProviderRepository.Login>,
) {
    val all: List<ProviderRepository.Login> get() = listOf(main) + extras

    fun has(username: String): Boolean = all.any { it.username == username }

    sealed interface Change {
        data class Done(
            val logins: SourceLogins,
        ) : Change

        /** The username is already one of the source's logins. */
        data object DuplicateUsername : Change

        /** No login with that username. */
        data object UnknownUsername : Change

        /** The source's only login can't be removed: that is deleting the source. */
        data object LastLogin : Change

        /**
         * The login that would become the main one has no password on this device (it came from
         * an import): the main login must have one. Removing it and adding it again fixes that.
         */
        data object PasswordNeeded : Change
    }

    /** Adds an extra login at the end. Blank usernames count as unknown. */
    fun add(
        username: String,
        password: String,
    ): Change {
        val name = username.trim()
        return when {
            name.isEmpty() -> Change.UnknownUsername
            has(name) -> Change.DuplicateUsername
            else -> Change.Done(copy(extras = extras + ProviderRepository.Login(name, password)))
        }
    }

    /**
     * Removes [username]. Removing the main login promotes the first extra one with a password,
     * so the source always keeps a main login it can sign in with.
     */
    fun remove(username: String): Change {
        val promoted = extras.firstOrNull { it.password.isNotEmpty() }
        return when {
            !has(username) -> Change.UnknownUsername
            extras.isEmpty() -> Change.LastLogin
            main.username != username -> Change.Done(copy(extras = extras.filterNot { it.username == username }))
            promoted == null -> Change.PasswordNeeded
            else -> Change.Done(SourceLogins(promoted, extras.filterNot { it.username == promoted.username }))
        }
    }

    /** Swaps [username] with the main login: the old main takes its place among the extras. */
    fun makeMain(username: String): Change {
        val index = extras.indexOfFirst { it.username == username }
        return when {
            main.username == username -> Change.Done(this)
            index < 0 -> Change.UnknownUsername
            extras[index].password.isEmpty() -> Change.PasswordNeeded
            else -> Change.Done(SourceLogins(extras[index], extras.toMutableList().also { it[index] = main }))
        }
    }
}
