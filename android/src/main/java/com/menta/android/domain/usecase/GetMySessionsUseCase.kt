package com.menta.android.domain.usecase

import com.menta.android.domain.model.MySessionsResult
import com.menta.android.domain.repository.PhysicalSessionsRepository
import javax.inject.Inject

class GetMySessionsUseCase @Inject constructor(
    private val repository: PhysicalSessionsRepository,
) {
    /** Blocking: call off the main thread. */
    operator fun invoke(): MySessionsResult = repository.mySessions()
}
