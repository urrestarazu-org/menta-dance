package com.menta.android.domain.usecase

import com.menta.android.domain.repository.SessionRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    operator fun invoke() = sessionRepository.logout()
}
