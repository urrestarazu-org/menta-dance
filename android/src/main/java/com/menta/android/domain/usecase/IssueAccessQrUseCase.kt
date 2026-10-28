package com.menta.android.domain.usecase

import com.menta.android.domain.model.AccessQrResult
import com.menta.android.domain.repository.PhysicalSessionsRepository
import javax.inject.Inject

class IssueAccessQrUseCase @Inject constructor(
    private val repository: PhysicalSessionsRepository,
) {
    /** Blocking: call off the main thread. */
    operator fun invoke(sessionId: String): AccessQrResult = repository.issueAccessQr(sessionId)
}
