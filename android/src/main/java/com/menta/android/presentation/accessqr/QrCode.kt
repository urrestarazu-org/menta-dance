package com.menta.android.presentation.accessqr

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Draws [matrix] as black modules on a white background with the quiet zone included. The colors are
 * fixed on purpose: scanners need dark-on-light even when the app is in dark theme.
 */
@Composable
fun QrCode(matrix: QrMatrix, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(Color.White)
        drawModules(matrix)
    }
}

private fun DrawScope.drawModules(matrix: QrMatrix) {
    val width = size.width.toInt()
    val height = size.height.toInt()
    val cell = qrCellSize(minOf(width, height), matrix.size)
    if (cell == 0) return
    val left = qrOrigin(width, matrix.size, cell) + QR_QUIET_ZONE_MODULES * cell
    val top = qrOrigin(height, matrix.size, cell) + QR_QUIET_ZONE_MODULES * cell
    val moduleSize = Size(cell.toFloat(), cell.toFloat())
    for (y in 0 until matrix.size) {
        for (x in 0 until matrix.size) {
            if (matrix[x, y]) {
                drawRect(Color.Black, Offset((left + x * cell).toFloat(), (top + y * cell).toFloat()), moduleSize)
            }
        }
    }
}
