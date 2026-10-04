package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import org.njarasoa.fijerena.core.player.diagnostics.CrashLog

/**
 * Whether this device has had to reset an encrypted credentials file it couldn't decrypt (a lost
 * or reset Keystore key) — so a login that now fails can say why instead of "check your username
 * and password". Flags only, in plain prefs: never a secret. See
 * docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-28.
 */
object CredentialStoreHealth {
    private const val TAG = "CredentialStoreHealth"
    private const val PREFS = "credential_store_health"

    /** [fileName] couldn't be opened and was deleted; its saved login is gone. */
    fun markLost(
        context: Context,
        fileName: String,
        cause: Throwable,
    ) {
        Log.e(TAG, "Encrypted credentials $fileName unreadable — reset; the login must be entered again", cause)
        CrashLog.record("credentials reset: $fileName", cause)
        prefs(context).edit { putBoolean(fileName, true) }
    }

    fun anyLost(context: Context): Boolean = prefs(context).all.isNotEmpty()

    /** A login was saved again: whatever was lost has been, or is being, re-entered. */
    fun clear(context: Context) = prefs(context).edit { clear() }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Stands in for an encrypted store that can't be created even after a reset: credentials
     * entered now work for this run of the app and are never written to disk — never a plaintext
     * file, which is what the fallback used to be.
     */
    class InMemoryPrefs : SharedPreferences {
        private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
        private val listeners = java.util.concurrent.CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): Map<String, *> = HashMap(values)

        override fun getString(
            key: String,
            defValue: String?,
        ): String? = values[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(
            key: String,
            defValues: Set<String>?,
        ): Set<String>? = values[key] as? Set<String> ?: defValues

        override fun getInt(
            key: String,
            defValue: Int,
        ): Int = values[key] as? Int ?: defValue

        override fun getLong(
            key: String,
            defValue: Long,
        ): Long = values[key] as? Long ?: defValue

        override fun getFloat(
            key: String,
            defValue: Float,
        ): Float = values[key] as? Float ?: defValue

        override fun getBoolean(
            key: String,
            defValue: Boolean,
        ): Boolean = values[key] as? Boolean ?: defValue

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            listeners.add(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
            listeners.remove(listener)
        }

        private inner class Editor : SharedPreferences.Editor {
            private val puts = HashMap<String, Any?>()
            private var clearFirst = false

            override fun putString(
                key: String,
                value: String?,
            ) = apply { puts[key] = value }

            override fun putStringSet(
                key: String,
                values: Set<String>?,
            ) = apply { puts[key] = values?.toSet() }

            override fun putInt(
                key: String,
                value: Int,
            ) = apply { puts[key] = value }

            override fun putLong(
                key: String,
                value: Long,
            ) = apply { puts[key] = value }

            override fun putFloat(
                key: String,
                value: Float,
            ) = apply { puts[key] = value }

            override fun putBoolean(
                key: String,
                value: Boolean,
            ) = apply { puts[key] = value }

            override fun remove(key: String) = apply { puts[key] = null }

            override fun clear() = apply { clearFirst = true }

            override fun commit(): Boolean {
                if (clearFirst) values.clear()
                puts.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                puts.keys.forEach { key -> listeners.forEach { it.onSharedPreferenceChanged(this@InMemoryPrefs, key) } }
                return true
            }

            override fun apply() {
                commit()
            }
        }
    }
}
