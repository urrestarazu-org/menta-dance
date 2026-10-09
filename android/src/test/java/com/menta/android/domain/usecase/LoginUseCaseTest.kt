package com.menta.android.domain.usecase

import com.menta.android.domain.model.AuthFailure
import com.menta.android.domain.model.Role
import com.menta.android.domain.model.SessionState
import com.menta.android.domain.model.StudentSessionResult
import com.menta.android.testing.FakeSessionRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class LoginUseCaseTest {
    private val repository = FakeSessionRepository()
    private val useCase = LoginUseCase(repository)

    @Test
    fun student_login_keeps_the_session() {
        repository.loginResult = SessionState.Authenticated(900, Role.STUDENT)

        assertEquals(StudentSessionResult.Student, useCase("a@b.c", "pw"))
        assertEquals(0, repository.logoutCalls)
    }

    @Test
    fun instructor_login_revokes_the_session_and_is_not_a_student() {
        repository.loginResult = SessionState.Authenticated(900, Role.INSTRUCTOR)

        assertEquals(StudentSessionResult.NotStudent, useCase("a@b.c", "pw"))
        assertEquals(1, repository.logoutCalls)
    }

    @Test
    fun missing_role_claim_is_treated_as_not_a_student() {
        repository.loginResult = SessionState.Authenticated(900, null)

        assertEquals(StudentSessionResult.NotStudent, useCase("a@b.c", "pw"))
        assertEquals(1, repository.logoutCalls)
    }

    @Test
    fun failure_is_passed_through_without_logout() {
        repository.loginResult = SessionState.Failed(AuthFailure.AccountLocked)

        assertEquals(StudentSessionResult.Failed(AuthFailure.AccountLocked), useCase("a@b.c", "pw"))
        assertEquals(0, repository.logoutCalls)
    }

    @Test
    fun signed_out_result_maps_to_signed_out() {
        repository.loginResult = SessionState.SignedOut

        assertEquals(StudentSessionResult.SignedOut, useCase("a@b.c", "pw"))
    }
}
