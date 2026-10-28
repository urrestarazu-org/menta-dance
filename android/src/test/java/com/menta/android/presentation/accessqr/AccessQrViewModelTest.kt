package com.menta.android.presentation.accessqr

import com.menta.android.domain.model.AccessCredential
import com.menta.android.domain.model.AccessDenial
import com.menta.android.domain.model.AccessQrResult
import com.menta.android.domain.usecase.IssueAccessQrUseCase
import com.menta.android.testing.FakePhysicalSessionsRepository
import com.menta.android.testing.MainDispatcherRule
import com.menta.android.testing.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccessQrViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val repository = FakePhysicalSessionsRepository()
    private val clock = TestClock(mainRule.dispatcher.scheduler)
    private val viewModel = AccessQrViewModel("s-1", IssueAccessQrUseCase(repository), clock, mainRule.dispatcher)

    /** A credential that expires [expiresInSeconds] after the test origin (not after "now"). */
    private fun credential(value: String, expiresInSeconds: Long = 300, refreshAfterSeconds: Long = 30) =
        AccessCredential(value, clock.origin.plusSeconds(expiresInSeconds), refreshAfterSeconds)

    private fun issued(value: String, expiresInSeconds: Long = 300, refreshAfterSeconds: Long = 30) =
        AccessQrResult.Issued(credential(value, expiresInSeconds, refreshAfterSeconds))

    /** Moves virtual time to [seconds] after the origin and runs everything due at that instant. */
    private fun TestScope.advanceToSecond(seconds: Long) {
        advanceTimeBy(seconds * 1000 - currentTime)
        runCurrent()
    }

    /** The loop never ends by itself, so every test must leave the screen hidden or `runTest` would wait forever. */
    private fun vmTest(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            viewModel.onHidden()
        }
    }

    @Test
    fun becoming_visible_issues_a_qr_for_the_session_and_shows_it() = vmTest {
        repository.accessQrScript += issued("qr:1")
        assertEquals(AccessQrUiState.Loading, viewModel.uiState.value)

        viewModel.onVisible()
        runCurrent()

        assertEquals(AccessQrUiState.Showing(credential("qr:1")), viewModel.uiState.value)
        assertEquals(listOf("s-1"), repository.accessQrRequests)
    }

    @Test
    fun the_qr_is_renewed_exactly_once_after_the_server_hint() = vmTest {
        repository.accessQrScript += issued("qr:1")
        repository.accessQrScript += issued("qr:2")

        viewModel.onVisible()
        advanceToSecond(29)
        assertEquals(1, repository.accessQrCalls)
        advanceToSecond(30)

        assertEquals(2, repository.accessQrCalls)
        assertEquals(AccessQrUiState.Showing(credential("qr:2")), viewModel.uiState.value)
        advanceToSecond(59)
        assertEquals(2, repository.accessQrCalls)
    }

    @Test
    fun becoming_hidden_stops_renewing_and_drops_the_credential() = vmTest {
        repository.accessQrResult = issued("qr:1")
        viewModel.onVisible()
        runCurrent()
        assertEquals(AccessQrUiState.Showing(credential("qr:1")), viewModel.uiState.value)

        viewModel.onHidden()
        advanceToSecond(600)

        assertEquals(1, repository.accessQrCalls)
        assertEquals(AccessQrUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun becoming_visible_again_restarts_the_loop() = vmTest {
        repository.accessQrScript += issued("qr:1")
        repository.accessQrScript += issued("qr:2")
        viewModel.onVisible()
        runCurrent()
        viewModel.onHidden()

        viewModel.onVisible()
        runCurrent()

        assertEquals(2, repository.accessQrCalls)
        assertEquals(AccessQrUiState.Showing(credential("qr:2")), viewModel.uiState.value)
    }

    @Test
    fun becoming_visible_twice_does_not_start_two_loops() = vmTest {
        repository.accessQrResult = issued("qr:1")

        viewModel.onVisible()
        viewModel.onVisible()
        advanceToSecond(30)

        assertEquals(2, repository.accessQrCalls)
    }

    @Test
    fun the_showing_state_never_prints_the_credential_value() {
        val text = AccessQrUiState.Showing(credential("qr:abc")).toString()

        assertFalse(text.contains("qr:abc"))
    }

    @Test
    fun every_denial_is_shown_without_a_qr_and_never_retried() = vmTest {
        AccessDenial.entries.forEach { reason ->
            repository.accessQrRequests.clear()
            repository.accessQrResult = AccessQrResult.Denied(reason)
            val vm = AccessQrViewModel("s-1", IssueAccessQrUseCase(repository), clock, mainRule.dispatcher)

            vm.onVisible()
            advanceTimeBy(600_000)
            runCurrent()

            assertEquals(reason.name, AccessQrUiState.Denied(reason), vm.uiState.value)
            assertEquals(reason.name, 1, repository.accessQrCalls)
        }
    }

    @Test
    fun a_denial_during_renewal_drops_the_displayed_qr_immediately() = vmTest {
        repository.accessQrScript += issued("qr:1")
        repository.accessQrScript += AccessQrResult.Denied(AccessDenial.OUTSIDE_CHECK_IN_WINDOW)
        viewModel.onVisible()
        runCurrent()
        assertEquals(AccessQrUiState.Showing(credential("qr:1")), viewModel.uiState.value)

        advanceToSecond(30)

        assertEquals(AccessQrUiState.Denied(AccessDenial.OUTSIDE_CHECK_IN_WINDOW), viewModel.uiState.value)
        advanceToSecond(600)
        assertEquals(2, repository.accessQrCalls)
    }

    @Test
    fun a_failed_renewal_keeps_the_same_qr_while_it_is_still_valid() = vmTest {
        repository.accessQrScript += issued("qr:1", expiresInSeconds = 50)
        repository.accessQrResult = AccessQrResult.Unavailable
        viewModel.onVisible()

        advanceToSecond(30)
        assertEquals(2, repository.accessQrCalls)
        advanceToSecond(40)

        assertEquals(AccessQrUiState.Showing(credential("qr:1", expiresInSeconds = 50)), viewModel.uiState.value)
    }

    @Test
    fun a_failure_past_expiry_clears_the_qr_and_retries_with_increasing_delays() = vmTest {
        repository.accessQrScript += issued("qr:1", expiresInSeconds = 20)
        repository.accessQrResult = AccessQrResult.Unavailable
        viewModel.onVisible()

        // Renewal at 15 s fails; retries are capped by the expiry (17 s, then 20 s) while the QR is valid.
        advanceToSecond(19)
        assertEquals(AccessQrUiState.Showing(credential("qr:1", expiresInSeconds = 20)), viewModel.uiState.value)
        assertEquals(3, repository.accessQrCalls)

        advanceToSecond(20)
        assertEquals(AccessQrUiState.Offline, viewModel.uiState.value)
        assertEquals(4, repository.accessQrCalls)

        // Past expiry the backoff keeps doubling: 8 s after the failure at 20 s, then 16 s.
        advanceToSecond(27)
        assertEquals(4, repository.accessQrCalls)
        advanceToSecond(28)
        assertEquals(5, repository.accessQrCalls)
        advanceToSecond(43)
        assertEquals(5, repository.accessQrCalls)
        advanceToSecond(44)
        assertEquals(6, repository.accessQrCalls)
        assertEquals(AccessQrUiState.Offline, viewModel.uiState.value)
    }

    @Test
    fun without_a_qr_the_backoff_is_2_4_8_16_then_capped_at_30_seconds() = vmTest {
        repository.accessQrResult = AccessQrResult.Unavailable
        viewModel.onVisible()
        runCurrent()
        assertEquals(AccessQrUiState.Offline, viewModel.uiState.value)

        // Call n happens at the cumulative sum of the previous delays: 0, 2, 6, 14, 30, 60, 90.
        val callTimes = listOf(0L, 2L, 6L, 14L, 30L, 60L, 90L)
        callTimes.forEachIndexed { index, second ->
            if (second > 0) {
                advanceToSecond(second - 1)
                assertEquals("before second $second", index, repository.accessQrCalls)
            }
            advanceToSecond(second)
            assertEquals("at second $second", index + 1, repository.accessQrCalls)
        }
    }

    @Test
    fun a_successful_retry_restores_the_qr_and_resets_the_backoff() = vmTest {
        repository.accessQrScript += AccessQrResult.Unavailable
        repository.accessQrScript += issued("qr:2")
        repository.accessQrScript += AccessQrResult.Unavailable
        repository.accessQrScript += issued("qr:3")
        viewModel.onVisible()
        runCurrent()
        assertEquals(AccessQrUiState.Offline, viewModel.uiState.value)

        advanceToSecond(2)
        assertEquals(AccessQrUiState.Showing(credential("qr:2")), viewModel.uiState.value)

        // The renewal at 32 s fails; the counter was reset, so the retry comes 2 s later, not 4 s.
        advanceToSecond(34)
        assertEquals(4, repository.accessQrCalls)
        assertEquals(AccessQrUiState.Showing(credential("qr:3")), viewModel.uiState.value)
    }
}
