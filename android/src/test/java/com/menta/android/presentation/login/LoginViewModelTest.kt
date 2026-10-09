package com.menta.android.presentation.login

import com.menta.android.domain.model.AuthFailure
import com.menta.android.domain.model.Role
import com.menta.android.domain.model.SessionState
import com.menta.android.domain.usecase.LoginUseCase
import com.menta.android.testing.FakeSessionRepository
import com.menta.android.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val repository = FakeSessionRepository()
    private val viewModel = LoginViewModel(LoginUseCase(repository), mainRule.dispatcher)

    private fun submit(email: String = "alumno@menta.com", password: String = "secret") {
        viewModel.onEmailChange(email)
        viewModel.onPasswordChange(password)
        viewModel.onSubmit()
    }

    @Test
    fun blank_email_shows_a_field_error_and_sends_no_request() = runTest {
        submit(email = "  ")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.emailRequired)
        assertFalse(viewModel.uiState.value.passwordRequired)
        assertEquals(0, repository.loginCalls)
    }

    @Test
    fun blank_password_shows_a_field_error_and_sends_no_request() = runTest {
        submit(password = "")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.passwordRequired)
        assertEquals(0, repository.loginCalls)
    }

    @Test
    fun editing_a_field_clears_its_error() = runTest {
        submit(email = "")
        viewModel.onEmailChange("a@b.c")

        assertFalse(viewModel.uiState.value.emailRequired)
    }

    @Test
    fun loading_is_true_while_the_request_runs_and_false_after() = runTest {
        repository.loginResult = SessionState.Authenticated(900, Role.STUDENT)

        submit()
        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun student_login_emits_the_signed_in_event() = runTest {
        repository.loginResult = SessionState.Authenticated(900, Role.STUDENT)
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.signedIn.toList(events) }

        submit()
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun each_failure_maps_to_its_login_error() = runTest {
        val expected = mapOf(
            AuthFailure.InvalidCredentials to LoginError.InvalidCredentials,
            AuthFailure.AccountNotActivated to LoginError.InvalidCredentials,
            AuthFailure.AccountLocked to LoginError.AccountLocked,
            AuthFailure.RateLimited(30) to LoginError.RateLimited(30),
            AuthFailure.RateLimited(null) to LoginError.RateLimited(null),
            AuthFailure.Unavailable to LoginError.Unavailable,
            AuthFailure.InvalidResponse to LoginError.Unavailable,
            AuthFailure.LocalStorageFailed to LoginError.Unavailable,
            AuthFailure.Unexpected("X") to LoginError.Unavailable,
        )
        expected.forEach { (failure, error) -> assertEquals(error, failure.toLoginError()) }

        repository.loginResult = SessionState.Failed(AuthFailure.RateLimited(30))
        submit()
        advanceUntilIdle()

        assertEquals(LoginError.RateLimited(30), viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun non_student_stays_on_login_with_the_students_only_notice() = runTest {
        repository.loginResult = SessionState.Authenticated(900, Role.INSTRUCTOR)
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.signedIn.toList(events) }

        submit()
        advanceUntilIdle()

        assertEquals(LoginError.StudentsOnly, viewModel.uiState.value.error)
        assertEquals(1, repository.logoutCalls)
        assertTrue(events.isEmpty())
    }

    @Test
    fun a_new_submit_clears_the_previous_error() = runTest {
        repository.loginResult = SessionState.Failed(AuthFailure.InvalidCredentials)
        submit()
        advanceUntilIdle()
        repository.loginResult = SessionState.Authenticated(900, Role.STUDENT)

        viewModel.onSubmit()

        assertNull(viewModel.uiState.value.error)
    }
}
