package com.menta.android.domain.repository

import com.menta.android.domain.model.AccessQrResult
import com.menta.android.domain.model.MySessionsResult

interface PhysicalSessionsRepository {
    /** Blocking: call off the main thread. */
    fun mySessions(): MySessionsResult

    /** Blocking: call off the main thread. Asks for a fresh access QR for [sessionId]. */
    fun issueAccessQr(sessionId: String): AccessQrResult
}
