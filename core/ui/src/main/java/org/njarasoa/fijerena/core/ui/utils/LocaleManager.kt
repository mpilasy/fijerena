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
        val locale = Locale.forLanguageTag(AppSettings(base).language)
        Locale.setDefault(locale)

        // Locale-only delta, NOT a copy of the current configuration: createConfigurationContext
        // treats every non-default field of the passed Configuration as a permanent override, so
        // copying the live config freezes orientation/screenWidthDp/uiMode/density at launch
        // values. MainActivity declares configChanges=orientation|screenSize, so it is never
        // recreated on rotation and attachBaseContext never re-runs — the frozen values would
        // stay wrong for the whole process (broke the landscape Live TV preview: LocalConfiguration
        // kept reporting portrait). Configuration.updateFrom() ignores unset fields, so an empty
        // Configuration with only the locale set overrides only the locale.
        val config = Configuration()
        // setToDefaults() (run by the constructor) sets fontScale = 1, which would override the
        // user's system font scale; 0 means "not set".
        config.fontScale = 0f
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Persist the chosen language. Caller recreates the activity to apply it. */
    fun updateLocale(context: Context, language: String) {
        AppSettings(context).language = language
    }
}
