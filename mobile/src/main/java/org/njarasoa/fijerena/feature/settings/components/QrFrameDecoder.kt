package org.njarasoa.fijerena.feature.settings.components

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader

/**
 * The QR codes in a camera frame's luminance (Y) plane, empty when there are none.
 * [rowStride] is the plane's bytes per row, which can be larger than [width] (row padding); the
 * last row may be cut short. Rotation needs no handling: the detector finds codes at any angle.
 * Light-on-dark codes are tried too.
 *
 * The multi-code reader, not `QRCodeReader`: that one stops at the first three finder-like
 * patterns, and a few codes hold such a pattern in their data, so they never read in one
 * orientation.
 */
internal fun decodeQrFrame(
    luminance: ByteArray,
    width: Int,
    height: Int,
    rowStride: Int,
): List<String> {
    val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
    return decode(source).ifEmpty { decode(source.invert()) }
}

private fun decode(source: LuminanceSource): List<String> =
    try {
        QRCodeMultiReader().decodeMultiple(BinaryBitmap(HybridBinarizer(source))).map { it.text }
    } catch (_: ReaderException) {
        emptyList()
    }
