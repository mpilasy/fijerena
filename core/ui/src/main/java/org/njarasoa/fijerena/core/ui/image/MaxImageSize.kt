package org.njarasoa.fijerena.core.ui.image

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.size.Dimension
import coil3.size.Size

/**
 * TV: no image is decoded larger than 1080p ([maxLongestPx] on its longest side). A 4K Shield lays
 * out at 3840 px, so a full-screen backdrop decoded to fit was 33 MB of bitmap; at 1080p it is 8 MB
 * and looks the same from the sofa. A 1080p TV asks for no more than that anyway.
 */
class MaxImageSizeInterceptor(
    private val maxLongestPx: Int = MAX_TV_IMAGE_PX,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val capped = cappedSize(chain.size, maxLongestPx)
        return if (capped == chain.size) chain.proceed() else chain.withSize(capped).proceed()
    }

    companion object {
        const val MAX_TV_IMAGE_PX = 1920
    }
}

/** [size] scaled down, keeping its shape, so neither side passes [maxLongestPx]; an unknown side stays unknown. */
fun cappedSize(
    size: Size,
    maxLongestPx: Int,
): Size {
    val width = (size.width as? Dimension.Pixels)?.px
    val height = (size.height as? Dimension.Pixels)?.px
    val longest = maxOf(width ?: 0, height ?: 0)
    if (longest <= maxLongestPx) return size
    val scale = maxLongestPx.toDouble() / longest
    return Size(
        width?.let { Dimension((it * scale).toInt().coerceAtLeast(1)) } ?: size.width,
        height?.let { Dimension((it * scale).toInt().coerceAtLeast(1)) } ?: size.height,
    )
}
