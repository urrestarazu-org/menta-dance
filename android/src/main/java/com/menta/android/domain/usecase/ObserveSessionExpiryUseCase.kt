package com.menta.android.domain.usecase

import com.menta.android.domain.repository.SessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSessionExpiryUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    operator fun invoke(): Flow<Unit> = sessionRepository.sessionExpired
}
