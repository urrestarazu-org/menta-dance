package com.menta.android.data.repository

import com.menta.android.data.remote.AuthRemoteResult
import com.menta.android.domain.model.SessionState
import com.menta.android.testing.InMemoryRefreshTokenStore
import com.menta.android.testing.RotatingServerRemote
import com.menta.android.testing.studentAccessToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AuthSessionRepositoryLogoutAndExpiryTest {
    private val remote = RotatingServerRemote("refresh-0")
    private val store = InMemoryRefreshTokenStore("refresh-0")
    private val repository = AuthSessionRepository(store, remote)

    private val unauthorized = AuthRemoteResult.Problem("UNAUTHORIZED", 401)
    private val loggedOut = AuthRemoteResult.Success(null, null, null)

    @Test
    fun logout_401_renews_once_and_retries_logout_with_the_new_pair() {
        signIn()
        remote.logoutOutcomes = listOf(unauthorized, loggedOut)

        repository.logout()

        assertEquals(2, remote.logoutCalls.get())
        assertEquals(1, refreshesSinceSignIn())
        assertEquals("refresh-2", remote.lastLogoutRefreshToken)
        assertEquals(studentAccessToken(2), remote.lastLogoutAccessToken)
        assertLocalSessionWiped()
    }

    @Test
    fun logout_that_succeeds_the_first_time_never_renews() {
        signIn()

        repository.logout()

        assertEquals(1, remote.logoutCalls.get())
        assertEquals(0, refreshesSinceSignIn())
        assertEquals("refresh-1", remote.lastLogoutRefreshToken)
        assertLocalSessionWiped()
    }

    @Test
    fun logout_failing_with_another_status_never_renews_and_still_wipes() {
        signIn()
        remote.logoutOutcomes = listOf(AuthRemoteResult.Problem("INTERNAL", 500))

        repository.logout()

        assertEquals(1, remote.logoutCalls.get())
        assertEquals(0, refreshesSinceSignIn())
        assertLocalSessionWiped()
    }

    @Test
    fun logout_renews_at_most_once_when_the_retry_is_unauthorized_again() {
        signIn()
        remote.logoutOutcomes = listOf(unauthorized)

        repository.logout()

        assertEquals(2, remote.logoutCalls.get())
        assertEquals(1, refreshesSinceSignIn())
        assertLocalSessionWiped()
    }

    @Test
    fun logout_401_with_a_rejected_renewal_sends_no_second_logout_and_still_wipes() {
        signIn()
        remote.logoutOutcomes = listOf(unauthorized)
        remote.refreshOutcome = AuthRemoteResult.Problem("INVALID_REFRESH_TOKEN", 401)

        repository.logout()

        assertEquals(1, remote.logoutCalls.get())
        assertLocalSessionWiped()
    }

    @Test
    fun logout_401_with_an_unavailable_renewal_sends_no_second_logout_and_still_wipes() {
        signIn()
        remote.logoutOutcomes = listOf(unauthorized)
        remote.refreshOutcome = AuthRemoteResult.Unavailable

        repository.logout()

        assertEquals(1, remote.logoutCalls.get())
        assertLocalSessionWiped()
    }

    @Test
    fun logout_wipes_even_when_the_retry_itself_throws() {
        signIn()
        remote.logoutOutcomes = listOf(unauthorized)
        remote.failLogoutFromCall = 1

        assertThrows(IllegalStateException::class.java) { repository.logout() }

        assertLocalSessionWiped()
    }

    @Test
    fun logout_never_signals_session_expiry() {
        signIn()
        remote.logoutOutcomes = listOf(unauthorized)
        remote.refreshOutcome = AuthRemoteResult.Problem("INVALID_REFRESH_TOKEN", 401)

        assertEquals(0, expirySignalsDuring { repository.logout() })
    }

    @Test
    fun a_rejected_renewal_signals_session_expiry_exactly_once() {
        signIn()
        remote.refreshOutcome = AuthRemoteResult.Problem("REFRESH_TOKEN_COMPROMISED", 401)

        assertEquals(1, expirySignalsDuring { repository.renewAfterUnauthorized(repository.currentAccessToken()!!) })
        assertLocalSessionWiped()
    }

    @Test
    fun an_unavailable_renewal_does_not_signal_expiry_and_keeps_the_token() {
        signIn()
        remote.refreshOutcome = AuthRemoteResult.Unavailable

        assertEquals(0, expirySignalsDuring { repository.renewAfterUnauthorized(repository.currentAccessToken()!!) })
        assertEquals("refresh-1", store.read())
    }

    @Test
    fun a_successful_renewal_does_not_signal_expiry() {
        signIn()

        assertEquals(0, expirySignalsDuring { repository.renewAfterUnauthorized(repository.currentAccessToken()!!) })
    }

    @Test
    fun expiring_the_session_wipes_it_and_signals_expiry() {
        signIn()

        assertEquals(1, expirySignalsDuring { repository.expireSession() })
        assertLocalSessionWiped()
    }

    @Test
    fun expiring_a_session_that_is_already_signed_out_signals_nothing() {
        store.clear()

        assertEquals(0, expirySignalsDuring { repository.expireSession() })
    }

    @Test
    fun a_cold_start_refresh_rejection_does_not_signal_expiry() {
        remote.refreshOutcome = AuthRemoteResult.Problem("INVALID_REFRESH_TOKEN", 401)

        assertEquals(0, expirySignalsDuring { repository.refresh() })
        assertNull(store.read())
    }

    @Test
    fun a_renewal_never_renders_the_access_token() {
        signIn()

        val renewal = repository.renewAfterUnauthorized(repository.currentAccessToken()!!)

        assertEquals("Renewed", renewal.toString())
    }

    private fun refreshesSinceSignIn(): Int = remote.refreshCalls.get() - SIGN_IN_REFRESHES

    private fun signIn() {
        check(repository.refresh() is SessionState.Authenticated)
    }

    private fun assertLocalSessionWiped() {
        assertNull(store.read())
        assertNull(repository.currentAccessToken())
    }

    /** Counts the expiry signals emitted while [action] runs; the collector is subscribed before it starts. */
    private fun expirySignalsDuring(action: () -> Unit): Int = runBlocking {
        var signals = 0
        val collector = launch(Dispatchers.Unconfined) { repository.sessionExpired.collect { signals++ } }
        action()
        collector.cancelAndJoin()
        signals
    }

    private companion object {
        const val SIGN_IN_REFRESHES = 1
    }
}
