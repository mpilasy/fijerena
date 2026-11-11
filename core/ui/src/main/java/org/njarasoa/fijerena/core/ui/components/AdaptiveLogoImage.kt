package org.njarasoa.fijerena.core.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.BitmapImage
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.ControlStyle
import org.njarasoa.fijerena.core.ui.theme.cornerShape

/**
 * TMDB logo art for [contentDescription]. A TMDB logo is not always light on transparent: some
 * titles' only logo is a dark wordmark (e.g. "Pluribus"), which would vanish on the dark UI. This
 * samples the loaded image and adds a backing plate only when the logo has the same lightness as
 * the theme behind it: a light plate for a dark logo on a dark theme, a dark plate for a light logo
 * (the common case) on a light theme.
 *
 * [modifier] carries the caller's sizing (e.g. `.height(x)`); the plate, when needed, wraps it.
 */
@Composable
fun AdaptiveLogoImage(
    logoUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {},
    alignment: Alignment = Alignment.CenterStart,
) {
    // Null until this logo's tone is known: cached from an earlier showing, or worked out off the
    // main thread once it loads (on Main, copying the hardware bitmap's pixels is a ~250 ms disk read
    // under StrictMode; the next level rock solid resilience plan → R-28). Invisible until then, a
    // frame or two, so a dark logo never flashes bare and the plate never shifts the layout.
    var isDark by remember(logoUrl) { mutableStateOf(LogoToneCache.get(logoUrl)) }
    val scope = rememberCoroutineScope()
    val c = CinemaTheme.colors
    // The plate decision compares the logo with the theme: isDark == the theme is dark.
    val plate: Boolean? = isDark?.let { it != c.isLight }
    AsyncImage(
        model = logoUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        alignment = alignment,
        onError = { onError() },
        onSuccess = { state ->
            val bitmap = (state.result.image as? BitmapImage)?.bitmap
            if (isDark == null) {
                scope.launch {
                    val dark =
                        withContext(Dispatchers.Default) {
                            // Best effort: a light logo with no plate (the common case) is a far better failure than
                            // a crash over a decorative check.
                            runCatching { bitmap?.let(::isDarkLogo) ?: false }.getOrDefault(false)
                        }
                    LogoToneCache.put(logoUrl, dark)
                    isDark = dark
                }
            }
        },
        modifier =
            when (plate) {
                null -> {
                    modifier.alpha(0f)
                }

                // A logo the same lightness as the background behind it vanishes: a dark wordmark on
                // a dark theme gets a light plate, a light one (most logos) on a light theme a dark one.
                true -> {
                    val plateColor =
                        if (c.isLight) c.mediaScrim.copy(alpha = LOGO_PLATE_DARK_ALPHA) else c.onMedia.copy(alpha = LOGO_PLATE_ALPHA)
                    modifier
                        .background(plateColor, cornerShape(ControlStyle.logoPlateCorner))
                        .padding(horizontal = ControlStyle.logoPlatePaddingHorizontal, vertical = ControlStyle.logoPlatePaddingVertical)
                }

                false -> {
                    modifier
                }
            },
    )
}

/**
 * Each logo URL's tone ([isDarkLogo]) for this process, so a logo seen before (every reopening of
 * the player's controls, a detail screen revisited) shows at once with its plate already decided.
 * Bounded: least recently used entries go past [MAX_ENTRIES].
 */
internal object LogoToneCache {
    private const val MAX_ENTRIES = 256

    private val tones =
        object : LinkedHashMap<String, Boolean>(MAX_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > MAX_ENTRIES
        }

    @Synchronized
    fun get(url: String): Boolean? = tones[url]

    @Synchronized
    fun put(
        url: String,
        isDark: Boolean,
    ) {
        tones[url] = isDark
    }
}

/**
 * Average perceived luminance of the bitmap's opaque pixels, sampled from a tiny (16x16) scaled
 * copy. Off the main thread: StrictMode flags the hardware-bitmap copy there as a disk read.
 * Transparent pixels (most of a wordmark's bounding box) are skipped, so they do not pull a light
 * logo toward "dark" ([isDarkPixels]).
 */
private fun isDarkLogo(bitmap: Bitmap): Boolean {
    // Coil gives a Config.HARDWARE bitmap by default, whose pixels cannot be read (getPixel() throws),
    // also on a copy scaled from it. Bitmap.copy() moves them onto the CPU.
    val readable =
        if (bitmap.config == Bitmap.Config.HARDWARE) {
            // A hardware bitmap that won't copy leaves nothing readable to judge — treat as light.
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
    var isDark = false
    if (readable != null) {
        val sample = Bitmap.createScaledBitmap(readable, 16, 16, true)
        if (readable !== bitmap) readable.recycle()
        val pixels = IntArray(sample.width * sample.height)
        sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
        if (sample !== bitmap) sample.recycle()
        isDark = isDarkPixels(pixels)
    }
    return isDark
}

/** True when the average perceived luminance of the opaque ARGB [pixels] is below mid-grey. */
internal fun isDarkPixels(pixels: IntArray): Boolean {
    var totalLuminance = 0.0
    var opaquePixels = 0
    for (pixel in pixels) {
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha >= 32) {
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF
            totalLuminance += 0.299 * r + 0.587 * g + 0.114 * b
            opaquePixels++
        }
    }
    return opaquePixels > 0 && (totalLuminance / opaquePixels) < 128
}

private const val LOGO_PLATE_ALPHA = 0.9f
private const val LOGO_PLATE_DARK_ALPHA = 0.7f
