package com.menta.android.presentation.accessqr

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrEncoderTest {
    @Test
    fun a_placeholder_credential_round_trips_through_the_zxing_reader() {
        val credential = "qr:11111111-1111-1111-1111-111111111111:22222222-2222-2222-2222-222222222222:jti-1:1790000000"

        assertEquals(credential, decode(QrEncoder.encode(credential)))
    }

    @Test
    fun a_512_character_credential_encodes_and_round_trips() {
        val credential = "qr:" + (0 until 509).map { ALPHABET[it % ALPHABET.length] }.joinToString("")
        assertEquals(512, credential.length)

        assertEquals(credential, decode(QrEncoder.encode(credential)))
    }

    @Test
    fun the_matrix_is_square_with_a_size_that_grows_with_the_payload() {
        val short = QrEncoder.encode("qr:a")
        val long = QrEncoder.encode("qr:" + "a".repeat(509))

        assertNotNull(short)
        assertNotNull(long)
        // QR version 1 is 21 modules wide; 512 characters need a much bigger symbol.
        assertEquals(21, short!!.size)
        assertTrue(long!!.size > short.size)
        // The finder pattern makes the top-left module dark, so `get` reads real data.
        assertTrue(short[0, 0])
        assertTrue(!short[1, 1])
    }

    @Test
    fun a_payload_beyond_the_qr_capacity_is_an_error_value_not_an_exception() {
        assertNull(QrEncoder.encode("a".repeat(4000)))
    }

    @Test
    fun toString_reports_only_the_size() {
        val matrix = QrEncoder.encode("qr:secret-credential")!!

        assertEquals("QrMatrix(size=${matrix.size})", matrix.toString())
    }

    @Test
    fun cell_size_is_the_integer_floor_of_the_space_over_the_modules_plus_the_quiet_zone() {
        assertEquals(10, qrCellSize(minPx = 290, modules = 21))
        assertEquals(10, qrCellSize(minPx = 299, modules = 21))
        assertEquals(9, qrCellSize(minPx = 289, modules = 21))
        assertEquals(0, qrCellSize(minPx = 28, modules = 21))
    }

    @Test
    fun the_origin_centers_the_quiet_zone_square_in_the_available_space() {
        // 21 + 8 = 29 modules of 10 px = 290 px inside 299 px leaves 9 px, split 4/5.
        assertEquals(4, qrOrigin(minPx = 299, modules = 21, cellPx = 10))
        assertEquals(0, qrOrigin(minPx = 290, modules = 21, cellPx = 10))
    }

    private fun decode(matrix: QrMatrix?): String {
        assertNotNull(matrix)
        val total = matrix!!.size + 2 * QUIET_ZONE_MODULES
        val side = total * PX_PER_MODULE
        val pixels = IntArray(side * side) { WHITE }
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (!matrix[x, y]) continue
                val left = (x + QUIET_ZONE_MODULES) * PX_PER_MODULE
                val top = (y + QUIET_ZONE_MODULES) * PX_PER_MODULE
                for (dy in 0 until PX_PER_MODULE) {
                    for (dx in 0 until PX_PER_MODULE) pixels[(top + dy) * side + left + dx] = BLACK
                }
            }
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))
        return QRCodeReader().decode(bitmap).text
    }

    private companion object {
        const val QUIET_ZONE_MODULES = 4
        const val PX_PER_MODULE = 4
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
        const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789-:"
    }
}
