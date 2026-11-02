package org.njarasoa.fijerena.core.ui.utils

import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import io.mockk.every
import io.mockk.mockk
import org.njarasoa.fijerena.core.ui.R
import java.util.Locale

/**
 * Resources whose configuration is in [locale], with the size units of the app language
 * ("o", "Ko", "Mo", "Go" in French; "B", "KB", "MB", "GB" else).
 */
fun resourcesIn(locale: Locale): Resources {
    val french = locale.language == "fr"
    val units =
        mapOf(
            R.string.size_bytes to if (french) "o" else "B",
            R.string.size_kilobytes to if (french) "Ko" else "KB",
            R.string.size_megabytes to if (french) "Mo" else "MB",
            R.string.size_gigabytes to if (french) "Go" else "GB",
        )
    val localeList = mockk<LocaleList> { every { get(0) } returns locale }
    val config = mockk<Configuration> { every { locales } returns localeList }
    return mockk {
        every { configuration } returns config
        units.forEach { (id, unit) ->
            every { getString(id, *anyVararg()) } answers { "${(args[1] as Array<*>)[0]} $unit" }
        }
    }
}
