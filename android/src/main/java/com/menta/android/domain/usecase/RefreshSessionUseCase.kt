package com.menta.android.domain.usecase

import com.menta.android.domain.model.SessionState
import com.menta.android.domain.repository.SessionRepository
import javax.inject.Inject

class RefreshSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    operator fun invoke(): SessionState = sessionRepository.refresh()
}
