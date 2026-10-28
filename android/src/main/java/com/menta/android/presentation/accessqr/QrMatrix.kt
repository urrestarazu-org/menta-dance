package com.menta.android.presentation.accessqr

/** Square grid of QR modules (`true` = dark). [toString] never prints the modules, which encode the credential. */
class QrMatrix(val size: Int, private val modules: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]

    override fun toString(): String = "QrMatrix(size=$size)"
}
