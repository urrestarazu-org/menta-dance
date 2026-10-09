package com.menta.android.domain.usecase

import com.menta.android.domain.model.AuthFailure
import com.menta.android.domain.model.Role
import com.menta.android.domain.model.SessionState
import com.menta.android.domain.model.StudentSessionResult
import com.menta.android.testing.FakeSessionRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshSessionUseCaseTest {
    private val repository = FakeSessionRepository()
    private val useCase = RefreshSessionUseCase(repository)

    @Test
    fun student_session_is_resumed() {
        repository.refreshResult = SessionState.Authenticated(900, Role.STUDENT)

        assertEquals(StudentSessionResult.Student, useCase())
        assertEquals(0, repository.logoutCalls)
    }

    @Test
    fun non_student_session_is_revoked_and_rejected() {
        repository.refreshResult = SessionState.Authenticated(900, Role.INSTRUCTOR)

        assertEquals(StudentSessionResult.NotStudent, useCase())
        assertEquals(1, repository.logoutCalls)
    }

    @Test
    fun missing_role_claim_is_treated_as_not_a_student() {
        repository.refreshResult = SessionState.Authenticated(900, null)

        assertEquals(StudentSessionResult.NotStudent, useCase())
        assertEquals(1, repository.logoutCalls)
    }

    @Test
    fun no_stored_token_is_signed_out() {
        repository.refreshResult = SessionState.SignedOut

        assertEquals(StudentSessionResult.SignedOut, useCase())
        assertEquals(0, repository.logoutCalls)
    }

    @Test
    fun rejected_and_unavailable_refresh_are_passed_through_without_logout() {
        repository.refreshResult = SessionState.Failed(AuthFailure.SessionRejected)
        assertEquals(StudentSessionResult.Failed(AuthFailure.SessionRejected), useCase())

        repository.refreshResult = SessionState.Failed(AuthFailure.Unavailable)
        assertEquals(StudentSessionResult.Failed(AuthFailure.Unavailable), useCase())

        assertEquals(0, repository.logoutCalls)
    }
}
