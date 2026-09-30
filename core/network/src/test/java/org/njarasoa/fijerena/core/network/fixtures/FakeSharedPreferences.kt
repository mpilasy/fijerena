package org.njarasoa.fijerena.core.network.fixtures

import android.content.SharedPreferences

/** In-memory [SharedPreferences]; edits apply on commit/apply. Listeners are not supported. */
class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key] as MutableSet<String>? ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as Int? ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as Long? ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as Float? ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue

    override fun contains(key: String): Boolean = key in values

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val puts = mutableMapOf<String, Any?>()
        private val removes = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = apply { puts[key] = value }

        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { puts[key] = values }

        override fun putInt(key: String, value: Int) = apply { puts[key] = value }

        override fun putLong(key: String, value: Long) = apply { puts[key] = value }

        override fun putFloat(key: String, value: Float) = apply { puts[key] = value }

        override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }

        override fun remove(key: String) = apply { removes += key }

        override fun clear() = apply { clear = true }

        override fun commit(): Boolean {
            if (clear) values.clear()
            removes.forEach { values.remove(it) }
            values.putAll(puts)
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
