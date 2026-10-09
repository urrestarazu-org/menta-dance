package com.menta.android.domain.usecase

import com.menta.android.domain.model.StudentSessionResult
import com.menta.android.domain.repository.SessionRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    /** Blocking: call off the main thread. A non-student session is revoked before returning. */
    operator fun invoke(email: String, password: String): StudentSessionResult =
        sessionRepository.gateStudent(sessionRepository.login(email, password))
}
