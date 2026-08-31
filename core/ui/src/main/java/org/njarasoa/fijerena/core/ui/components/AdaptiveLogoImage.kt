package org.njarasoa.fijerena.core.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.BitmapImage
import coil3.compose.AsyncImage

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
) {
    var isDark by remember(logoUrl) { mutableStateOf(false) }
    AsyncImage(
        model = logoUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        onSuccess = { state ->
            // Best-effort: a plate-less light logo (the common case, and the prior behavior) is
            // a far better failure mode than crashing the player over a decorative check.
            runCatching {
                (state.result.image as? BitmapImage)?.bitmap?.let { isDark = isDarkLogo(it) }
            }
        },
        modifier =
            if (isDark) {
                modifier
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            } else {
                modifier
            },
    )
}

/**
 * Average perceived luminance of the bitmap's opaque pixels, sampled off a tiny (16x16) scaled
 * copy — cheap enough to run inline on the main thread. Transparent pixels (the vast majority of
 * a wordmark's own bounding box) are skipped so they don't pull a light logo's average toward
 * "dark" just because most of the image is empty.
 */
private fun isDarkLogo(bitmap: Bitmap): Boolean {
    // Coil hands back a Config.HARDWARE bitmap by default (GPU-backed, for efficient drawing) —
    // its pixels can't be read directly (getPixel() throws), on this or any bitmap scaled from
    // it. Bitmap.copy() is the supported way to pull a hardware bitmap's pixels onto the CPU.
    val readable =
        if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return false
        } else {
            bitmap
        }
    val sample = Bitmap.createScaledBitmap(readable, 16, 16, true)
    if (readable !== bitmap) readable.recycle()
    var totalLuminance = 0.0
    var opaquePixels = 0
    for (x in 0 until sample.width) {
        for (y in 0 until sample.height) {
            val pixel = sample.getPixel(x, y)
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 32) continue
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF
            totalLuminance += 0.299 * r + 0.587 * g + 0.114 * b
            opaquePixels++
        }
    }
    if (sample !== bitmap) sample.recycle()
    if (opaquePixels == 0) return false
    return (totalLuminance / opaquePixels) < 128
}
