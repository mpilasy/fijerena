package org.njarasoa.fijerena.core.ui.utils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import org.njarasoa.fijerena.core.network.AppSettings

object LocaleManager {
    fun applyLocale(context: Context) {
        val appSettings = AppSettings(context)
        val language = appSettings.language
        updateResources(context, language)
    }

    fun updateLocale(context: Context, language: String) {
        val appSettings = AppSettings(context)
        appSettings.language = language
        updateResources(context, language)
    }

    private fun updateResources(context: Context, language: String) {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocale(locale)
        
        // Update both context and system/base resources
        context.createConfigurationContext(config)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)
    }
}
