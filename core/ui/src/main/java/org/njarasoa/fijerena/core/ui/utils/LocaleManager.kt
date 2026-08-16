package org.njarasoa.fijerena.core.ui.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import org.njarasoa.fijerena.core.network.AppSettings

object LocaleManager {
    /**
     * Wrap an activity's base context with the saved app language.
     * Must be called from Activity.attachBaseContext so the activity's Resources
     * are created with the locale already applied. Patching Resources after
     * super.onCreate() does not survive: the framework re-applies the system
     * configuration and the UI falls back to the phone language.
     */
    fun wrap(base: Context): Context {
        val locale = Locale(AppSettings(base).language)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Persist the chosen language. Caller recreates the activity to apply it. */
    fun updateLocale(context: Context, language: String) {
        AppSettings(context).language = language
    }
}
