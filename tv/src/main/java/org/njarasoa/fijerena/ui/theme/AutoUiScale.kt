package org.njarasoa.fijerena.ui.theme

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * Text & grid size on Auto: from the best resolution the screen takes (the largest mode it lists,
 * not the current one — a Shield drops to the video's resolution while matching content, and the
 * menus mustn't change size with it). A 4K screen gets 4K (60%), 1080p gets HD (80%), anything
 * smaller SD (100%); HD when the screen says nothing.
 */
fun autoUiScale(context: Context): Float {
    val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    val longest = display?.supportedModes?.maxOfOrNull { maxOf(it.physicalWidth, it.physicalHeight) } ?: 0
    return uiScaleForScreen(longest)
}

/** [autoUiScale]'s choice for a screen whose longest side is [longestPx] pixels (0: unknown). */
fun uiScaleForScreen(longestPx: Int): Float =
    when {
        longestPx >= 3840 -> 0.6f
        longestPx >= 1920 || longestPx <= 0 -> 0.8f
        else -> 1.0f
    }
