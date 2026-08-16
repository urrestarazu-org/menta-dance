package com.menta.android.domain.usecase

import com.menta.android.domain.model.SessionState
import com.menta.android.domain.repository.SessionRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    operator fun invoke(email: String, password: String): SessionState = sessionRepository.login(email, password)
}
