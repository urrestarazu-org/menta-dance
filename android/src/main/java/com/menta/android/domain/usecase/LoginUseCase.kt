package com.menta.android.domain.usecase

import com.menta.android.domain.model.Role
import com.menta.android.domain.model.SessionState
import com.menta.android.domain.model.StudentSessionResult
import com.menta.android.domain.repository.SessionRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    /** Blocking: call off the main thread. A non-student session is revoked before returning. */
    operator fun invoke(email: String, password: String): StudentSessionResult =
        when (val state = sessionRepository.login(email, password)) {
            is SessionState.Authenticated ->
                if (state.role == Role.STUDENT) {
                    StudentSessionResult.Student
                } else {
                    sessionRepository.logout()
                    StudentSessionResult.NotStudent
                }
            SessionState.SignedOut -> StudentSessionResult.SignedOut
            is SessionState.Failed -> StudentSessionResult.Failed(state.failure)
        }
}
