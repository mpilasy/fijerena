package org.njarasoa.fijerena.feature.settings.components

fun formatProgrammeCount(count: Int): String =
    when {
        count >= 1000 -> String.format(java.util.Locale.US, "%.1fk", count / 1000.0)
        else -> count.toString()
    }
