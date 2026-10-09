package com.menta.android.presentation.session

import com.menta.android.data.repository.AuthSessionRepository
import com.menta.android.domain.model.AuthFailure
import com.menta.android.domain.model.Role
import com.menta.android.domain.model.SessionState
import com.menta.android.domain.usecase.LogoutUseCase
import com.menta.android.domain.usecase.ObserveSessionExpiryUseCase
import com.menta.android.domain.usecase.RefreshSessionUseCase
import com.menta.android.testing.FakeSessionRepository
import com.menta.android.testing.InMemoryRefreshTokenStore
import com.menta.android.testing.MainDispatcherRule
import com.menta.android.testing.RotatingServerRemote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val repository = FakeSessionRepository()
    private val expiry = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun viewModel(sessionRepository: com.menta.android.domain.repository.SessionRepository = repository) =
        SessionViewModel(
            RefreshSessionUseCase(sessionRepository),
            LogoutUseCase(sessionRepository),
            ObserveSessionExpiryUseCase(sessionRepository),
            mainRule.dispatcher,
        )

    @Test
    fun the_gate_starts_on_splash_while_the_session_resumes() = runTest {
        val vm = viewModel()

        assertEquals(SessionGate.Splash, vm.gate.value)
    }

    @Test
    fun student_session_resumes_to_signed_in() = runTest {
        repository.refreshResult = SessionState.Authenticated(900, Role.STUDENT)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(SessionGate.SignedIn, vm.gate.value)
    }

    @Test
    fun no_stored_token_goes_to_signed_out_without_a_notice() = runTest {
        repository.refreshResult = SessionState.SignedOut
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(SessionGate.SignedOut(null), vm.gate.value)
    }

    @Test
    fun rejected_token_goes_to_signed_out_with_the_rejected_notice() = runTest {
        repository.refreshResult = SessionState.Failed(AuthFailure.SessionRejected)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(SessionGate.SignedOut(SessionNotice.ResumeRejected), vm.gate.value)
    }

    @Test
    fun unavailable_api_goes_to_signed_out_with_the_offline_notice_and_keeps_the_token() = runTest {
        repository.refreshResult = SessionState.Failed(AuthFailure.Unavailable)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(SessionGate.SignedOut(SessionNotice.ResumeUnavailable), vm.gate.value)
        assertEquals(0, repository.logoutCalls)
    }

    @Test
    fun non_student_goes_to_signed_out_with_the_students_only_notice() = runTest {
        repository.refreshResult = SessionState.Authenticated(900, Role.INSTRUCTOR)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(SessionGate.SignedOut(SessionNotice.StudentsOnly), vm.gate.value)
        assertEquals(1, repository.logoutCalls)
    }

    @Test
    fun a_successful_login_moves_the_gate_to_signed_in() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSignedIn()

        assertEquals(SessionGate.SignedIn, vm.gate.value)
    }

    @Test
    fun logout_revokes_and_goes_to_signed_out_without_a_notice() = runTest {
        repository.refreshResult = SessionState.Authenticated(900, Role.STUDENT)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onLogout()
        advanceUntilIdle()

        assertEquals(1, repository.logoutCalls)
        assertEquals(SessionGate.SignedOut(null), vm.gate.value)
    }

    @Test
    fun logout_while_offline_still_wipes_the_local_session() = runTest {
        val remote = RotatingServerRemote("refresh-0").apply { failLogoutFromCall = 0 }
        val store = InMemoryRefreshTokenStore("refresh-0")
        val vm = viewModel(AuthSessionRepository(store, remote))
        advanceUntilIdle()
        vm.onSignedIn()

        vm.onLogout()
        advanceUntilIdle()

        assertNull(store.read())
        assertEquals(SessionGate.SignedOut(null), vm.gate.value)
    }

    @Test
    fun session_expiry_routes_to_signed_out_with_the_expired_notice() = runTest {
        repository.sessionExpired = expiry
        repository.refreshResult = SessionState.Authenticated(900, Role.STUDENT)
        val vm = viewModel()
        advanceUntilIdle()

        expiry.emit(Unit)
        advanceUntilIdle()

        assertEquals(SessionGate.SignedOut(SessionNotice.SessionExpired), vm.gate.value)
    }
}
