package com.menta.android.domain.repository

import com.menta.android.domain.model.AccessQrResult
import com.menta.android.domain.model.MySessionsResult
import java.time.Instant

interface PhysicalSessionsRepository {
    /** Blocking: call off the main thread. Sessions scheduled in the half-open UTC range `[from, to)`. */
    fun mySessions(from: Instant, to: Instant): MySessionsResult

    /** Blocking: call off the main thread. Asks for a fresh access QR for [sessionId]. */
    fun issueAccessQr(sessionId: String): AccessQrResult
}
