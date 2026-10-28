package com.menta.android.presentation.accessqr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Width of the blank border around the symbol, in modules (the QR specification asks for 4). */
const val QR_QUIET_ZONE_MODULES = 4

/** Pure JVM QR encoding; drawing is left to the composable. */
object QrEncoder {
    private val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 0,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )

    /** Returns `null` when [text] does not fit in a QR symbol, so the screen can show an error message. */
    fun encode(text: String): QrMatrix? = try {
        val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val size = bits.width
        QrMatrix(size, BooleanArray(size * size) { bits[it % size, it / size] })
    } catch (_: WriterException) {
        null
    }
}

/** Largest whole-pixel module size that fits [modules] plus the quiet zone in [minPx]. */
fun qrCellSize(minPx: Int, modules: Int): Int = minPx / (modules + 2 * QR_QUIET_ZONE_MODULES)

/** Offset that centers the quiet-zone square of [cellPx]-sized modules inside [minPx]. */
fun qrOrigin(minPx: Int, modules: Int, cellPx: Int): Int = (minPx - cellPx * (modules + 2 * QR_QUIET_ZONE_MODULES)) / 2
