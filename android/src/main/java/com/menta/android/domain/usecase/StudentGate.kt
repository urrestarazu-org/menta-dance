package com.menta.android.domain.usecase

import com.menta.android.domain.model.Role
import com.menta.android.domain.model.SessionState
import com.menta.android.domain.model.StudentSessionResult
import com.menta.android.domain.repository.SessionRepository

/** Applies the students-only gate; a non-student session is revoked before returning. */
internal fun SessionRepository.gateStudent(state: SessionState): StudentSessionResult =
    when (state) {
        is SessionState.Authenticated ->
            if (state.role == Role.STUDENT) {
                StudentSessionResult.Student
            } else {
                logout()
                StudentSessionResult.NotStudent
            }
        SessionState.SignedOut -> StudentSessionResult.SignedOut
        is SessionState.Failed -> StudentSessionResult.Failed(state.failure)
    }
