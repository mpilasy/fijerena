package org.njarasoa.fijerena.feature.settings.components

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.sync.PairingQr

class QrFrameDecoderTest {
    private val text = PairingQr.encode(PairingQr.Invite("https://sync.example.com", "ABCD-1234", ByteArray(32) { it.toByte() }))
    private val size = 300

    /** A Y plane holding [text] as a QR code, each row [rowStride] bytes long and the last one cut short. */
    private fun frame(
        rowStride: Int,
        inverted: Boolean = false,
        rotated: Boolean = false,
    ): ByteArray {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val dark: Byte = if (inverted) 0xE0.toByte() else 0x20
        val light: Byte = if (inverted) 0x20 else 0xE0.toByte()
        val bytes = ByteArray(rowStride * (size - 1) + size) { 0x7F }
        for (y in 0 until size) {
            for (x in 0 until size) {
                val set = if (rotated) matrix[y, size - 1 - x] else matrix[x, y]
                bytes[y * rowStride + x] = if (set) dark else light
            }
        }
        return bytes
    }

    @Test
    fun `reads a tightly packed frame`() {
        assertEquals(listOf(text), decodeQrFrame(frame(rowStride = size), size, size, size))
    }

    @Test
    fun `reads a frame with padded rows`() {
        assertEquals(listOf(text), decodeQrFrame(frame(rowStride = size + 64), size, size, size + 64))
    }

    // This code turned 90° holds a finder-like pattern in its data, which QRCodeReader alone misses.
    @Test
    fun `reads a sideways frame, as the sensor delivers it on a portrait phone`() {
        assertEquals(listOf(text), decodeQrFrame(frame(rowStride = size + 16, rotated = true), size, size, size + 16))
    }

    @Test
    fun `reads a light-on-dark code`() {
        assertEquals(listOf(text), decodeQrFrame(frame(rowStride = size, inverted = true), size, size, size))
    }

    @Test
    fun `nothing when there is no code`() {
        assertEquals(emptyList<String>(), decodeQrFrame(ByteArray(size * size) { 0x7F }, size, size, size))
    }
}
