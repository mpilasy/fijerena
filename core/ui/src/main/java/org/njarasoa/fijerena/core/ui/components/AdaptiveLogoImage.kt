package org.njarasoa.fijerena.core.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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

/**
 * TMDB logo art for [contentDescription], TMDB isn't guaranteed to be light-on-transparent —
 * some titles' only logo is a dark wordmark meant for a light background (e.g. "Pluribus"'s is
 * near-black), which would otherwise vanish against this app's dark UI. Samples the loaded image
 * and adds a light backing plate only when it's actually dark, so the (more common) light logos
 * keep rendering exactly as before — bare, no plate.
 *
 * [modifier] should carry the caller's sizing (e.g. `.height(x)`); the plate, when needed, wraps
 * around that.
 */
@Composable
fun AdaptiveLogoImage(
    logoUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onError: () -> Unit = {},
) {
    // Null until this logo's tone is known: cached from an earlier showing, or worked out off the
    // main thread once it loads (on the main thread, copying the hardware bitmap's pixels drew a
    // ~250 ms StrictMode disk read on a Shield — see
    // docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-28). Kept invisible until then, a frame or two, so a dark logo never flashes bare on the
    // dark UI and the plate's padding never shifts the layout after it has shown.
    var isDark by remember(logoUrl) { mutableStateOf(LogoToneCache.get(logoUrl)) }
    val scope = rememberCoroutineScope()
    AsyncImage(
        model = logoUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        onError = { onError() },
        onSuccess = { state ->
            val bitmap = (state.result.image as? BitmapImage)?.bitmap
            if (isDark == null) {
                scope.launch {
                    val dark =
                        withContext(Dispatchers.Default) {
                            // Best-effort: a plate-less light logo (the common case, and the prior
                            // behavior) is a far better failure mode than crashing the player over
                            // a decorative check.
                            runCatching { bitmap?.let(::isDarkLogo) ?: false }.getOrDefault(false)
                        }
                    LogoToneCache.put(logoUrl, dark)
                    isDark = dark
                }
            }
        },
        modifier =
            when (isDark) {
                true -> {
                    modifier
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                }

                false -> {
                    modifier
                }

                null -> {
                    modifier.alpha(0f)
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
 * Average perceived luminance of the bitmap's opaque pixels, sampled off a tiny (16x16) scaled
 * copy. Off the main thread: StrictMode flags the hardware-bitmap copy there as a disk read. Transparent pixels
 * (the vast majority of a wordmark's own bounding box) are skipped so they don't pull a light
 * logo's average toward "dark" just because most of the image is empty — see [isDarkPixels].
 */
private fun isDarkLogo(bitmap: Bitmap): Boolean {
    // Coil hands back a Config.HARDWARE bitmap by default (GPU-backed, for efficient drawing) —
    // its pixels can't be read directly (getPixel() throws), on this or any bitmap scaled from
    // it. Bitmap.copy() is the supported way to pull a hardware bitmap's pixels onto the CPU.
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
