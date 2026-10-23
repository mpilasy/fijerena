package org.njarasoa.fijerena.core.ui.utils

import android.content.Context
import android.content.res.Configuration
import org.njarasoa.fijerena.core.network.AppSettings
import java.util.Locale

object LocaleManager {
    /**
     * Wrap an activity's base context with the saved app language.
     * Must be called from Activity.attachBaseContext so the activity's Resources
     * are created with the locale already applied. Patching Resources after
     * super.onCreate() does not survive: the framework re-applies the system
     * configuration and the UI falls back to the phone language.
     */
    fun wrap(base: Context): Context {
        val locale = localeFor(AppSettings(base).language)
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

    /**
     * The app's locale for [language]: English is UK English (en-GB), so dates, numbers and the
     * region used for release dates are British whatever the device's region (user, 2026-10-22).
     */
    fun localeFor(language: String): Locale = if (language == ENGLISH) Locale.UK else Locale.forLanguageTag(language)

    private const val ENGLISH = "en"

    /** Persist the chosen language. Caller recreates the activity to apply it. */
    fun updateLocale(
        context: Context,
        language: String,
    ) {
        AppSettings(context).language = language
        // The application context's resources follow at once, so text looked up through it
        // (ViewModels) switches with the recreated activity rather than at the next launch.
        val resources = context.applicationContext.resources
        val config = Configuration(resources.configuration)
        val locale = localeFor(language)
        Locale.setDefault(locale)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
