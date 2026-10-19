package com.menta.android.data.repository

import com.menta.android.data.remote.AuthRemoteDataSource
import com.menta.android.data.remote.AuthRemoteResult
import com.menta.android.domain.model.AuthFailure
import com.menta.android.domain.model.Role
import com.menta.android.domain.model.SessionState
import com.menta.android.domain.repository.RefreshTokenStore
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AuthSessionRepositoryTest {
    @Test
    fun refresh_replaces_the_persisted_refresh_only_after_a_valid_response() {
        val store = FakeRefreshTokenStore("old-refresh")
        val repository = AuthSessionRepository(store, FakeRemote(success()))

        assertEquals(SessionState.Authenticated(900, Role.STUDENT), repository.refresh())
        assertEquals("new-refresh", store.read())
    }

    @Test
    fun refresh_without_a_stored_token_is_signed_out_and_makes_no_request() {
        val remote = FakeRemote(success())
        val repository = AuthSessionRepository(FakeRefreshTokenStore(null), remote)

        assertEquals(SessionState.SignedOut, repository.refresh())
        assertEquals(0, remote.calls)
    }

    @Test
    fun refresh_clears_local_state_when_rotated_refresh_cannot_be_persisted() {
        val store = FakeRefreshTokenStore("old-refresh", failOnReplace = true)
        val repository = AuthSessionRepository(store, FakeRemote(success()))

        assertEquals(SessionState.Failed(AuthFailure.LocalStorageFailed), repository.refresh())
        assertNull(store.read())
    }

    @Test
    fun persist_failure_fails_closed_and_never_retains_the_access_token() {
        val remote = FakeRemote(success())
        val repository = AuthSessionRepository(FakeRefreshTokenStore("old", failOnReplace = true), remote)

        repository.refresh()
        repository.logout()

        assertNull(remote.lastLogoutAccessToken)
    }

    @Test
    fun access_token_is_retained_once_the_rotated_refresh_is_persisted() {
        val token = studentJwt()
        val remote = FakeRemote(success(access = token))
        val repository = AuthSessionRepository(FakeRefreshTokenStore("old"), remote)

        repository.refresh()
        repository.logout()

        assertEquals(token, remote.lastLogoutAccessToken)
    }

    @Test
    fun logout_clears_local_tokens_when_remote_revocation_throws() {
        val store = FakeRefreshTokenStore("refresh")
        val repository = AuthSessionRepository(store, ThrowingRemote())

        assertThrows(IllegalStateException::class.java) { repository.logout() }

        assertNull(store.read())
    }

    @Test
    fun login_success_exposes_the_role_decoded_from_the_access_token() {
        val repository = AuthSessionRepository(
            FakeRefreshTokenStore(null),
            FakeRemote(success(access = jwt("""{"role":"INSTRUCTOR"}"""))),
        )

        assertEquals(SessionState.Authenticated(900, Role.INSTRUCTOR), repository.login("a@b.test", "pw"))
    }

    @Test
    fun login_success_without_a_role_claim_has_a_null_role() {
        val repository = AuthSessionRepository(
            FakeRefreshTokenStore(null),
            FakeRemote(success(access = jwt("""{"sub":"1"}"""))),
        )

        assertEquals(SessionState.Authenticated(900, null), repository.login("a@b.test", "pw"))
    }

    @Test
    fun login_success_with_an_opaque_access_token_has_a_null_role() {
        val repository = AuthSessionRepository(FakeRefreshTokenStore(null), FakeRemote(success(access = "opaque")))

        assertEquals(SessionState.Authenticated(900, null), repository.login("a@b.test", "pw"))
    }

    @Test
    fun login_success_with_missing_tokens_is_an_invalid_response_and_wipes() {
        val store = FakeRefreshTokenStore("stale")
        val repository = AuthSessionRepository(store, FakeRemote(AuthRemoteResult.Success(null, null, null)))

        assertEquals(SessionState.Failed(AuthFailure.InvalidResponse), repository.login("a@b.test", "pw"))
        assertNull(store.read())
    }

    @Test
    fun login_401_is_invalid_credentials() {
        assertLoginFailure(AuthRemoteResult.Problem("INVALID_CREDENTIALS", 401), AuthFailure.InvalidCredentials)
    }

    @Test
    fun login_403_is_account_not_activated() {
        assertLoginFailure(AuthRemoteResult.Problem("ACCOUNT_NOT_ACTIVATED", 403), AuthFailure.AccountNotActivated)
    }

    @Test
    fun login_403_for_a_non_operational_account_is_also_account_not_activated() {
        assertLoginFailure(AuthRemoteResult.Problem("ACCOUNT_NOT_OPERATIONAL", 403), AuthFailure.AccountNotActivated)
    }

    @Test
    fun login_423_is_account_locked() {
        assertLoginFailure(AuthRemoteResult.Problem("USER_LOCKED", 423), AuthFailure.AccountLocked)
    }

    @Test
    fun login_429_is_rate_limited_and_preserves_retry_after() {
        assertLoginFailure(AuthRemoteResult.Problem("LOGIN_RATE_LIMITED", 429, 30), AuthFailure.RateLimited(30))
    }

    @Test
    fun login_429_without_retry_after_is_rate_limited_with_null_seconds() {
        assertLoginFailure(AuthRemoteResult.Problem("LOGIN_RATE_LIMITED", 429), AuthFailure.RateLimited(null))
    }

    @Test
    fun login_503_is_unavailable() {
        assertLoginFailure(AuthRemoteResult.Problem("AUTH_DEGRADED", 503), AuthFailure.Unavailable)
    }

    @Test
    fun login_network_failure_is_unavailable() {
        assertLoginFailure(AuthRemoteResult.Unavailable, AuthFailure.Unavailable)
    }

    @Test
    fun login_unknown_problem_keeps_only_the_stable_code() {
        assertLoginFailure(AuthRemoteResult.Problem("SOMETHING_NEW", 500), AuthFailure.Unexpected("SOMETHING_NEW"))
    }

    @Test
    fun refresh_401_wipes_the_stored_token_and_is_a_session_rejection() {
        assertRefreshRejected(AuthRemoteResult.Problem("INVALID_REFRESH_TOKEN", 401))
    }

    @Test
    fun refresh_token_compromised_wipes_the_stored_token() {
        assertRefreshRejected(AuthRemoteResult.Problem("REFRESH_TOKEN_COMPROMISED", 401))
        assertRefreshRejected(AuthRemoteResult.Problem("REFRESH_TOKEN_COMPROMISED", 403))
    }

    @Test
    fun refresh_400_invalid_auth_request_wipes_the_stored_token() {
        assertRefreshRejected(AuthRemoteResult.Problem("INVALID_AUTH_REQUEST", 400))
    }

    @Test
    fun refresh_400_with_another_code_is_not_a_rejection_and_keeps_the_token() {
        assertRefreshKept(AuthRemoteResult.Problem("VALIDATION_FAILED", 400))
    }

    @Test
    fun refresh_offline_keeps_the_stored_token() {
        assertRefreshKept(AuthRemoteResult.Unavailable)
    }

    @Test
    fun refresh_503_auth_degraded_keeps_the_stored_token() {
        assertRefreshKept(AuthRemoteResult.Problem("AUTH_DEGRADED", 503))
    }

    @Test
    fun refresh_unexpected_server_error_keeps_the_stored_token() {
        assertRefreshKept(AuthRemoteResult.Problem("INTERNAL", 500))
    }

    @Test
    fun session_state_and_unexpected_failure_never_render_a_token() {
        val access = studentJwt()
        val repository = AuthSessionRepository(FakeRefreshTokenStore("old"), FakeRemote(success(access = access)))

        val rendered = listOf(
            repository.refresh().toString(),
            SessionState.Failed(AuthFailure.Unexpected("SOMETHING_NEW")).toString(),
            AuthFailure.RateLimited(30).toString(),
        ).joinToString()

        assertFalse(rendered.contains(access))
        assertFalse(rendered.contains("new-refresh"))
        assertFalse(rendered.contains("old"))
    }

    private fun assertLoginFailure(response: AuthRemoteResult, expected: AuthFailure) {
        val repository = AuthSessionRepository(FakeRefreshTokenStore(null), FakeRemote(response))

        assertEquals(SessionState.Failed(expected), repository.login("a@b.test", "pw"))
    }

    private fun assertRefreshRejected(response: AuthRemoteResult) {
        val store = FakeRefreshTokenStore("refresh")
        val repository = AuthSessionRepository(store, FakeRemote(response))

        assertEquals(SessionState.Failed(AuthFailure.SessionRejected), repository.refresh())
        assertNull(store.read())
    }

    private fun assertRefreshKept(response: AuthRemoteResult) {
        val store = FakeRefreshTokenStore("refresh")
        val repository = AuthSessionRepository(store, FakeRemote(response))

        assertEquals(SessionState.Failed(AuthFailure.Unavailable), repository.refresh())
        assertEquals("refresh", store.read())
    }

    private fun success(access: String = studentJwt()) = AuthRemoteResult.Success(access, "new-refresh", 900)

    private fun studentJwt() = jwt("""{"sub":"1","role":"STUDENT"}""")

    @OptIn(ExperimentalEncodingApi::class)
    private fun jwt(payload: String): String =
        "header." + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(payload.encodeToByteArray()) + ".sig"

    private class FakeRefreshTokenStore(
        private var value: String?,
        private val failOnReplace: Boolean = false,
    ) : RefreshTokenStore {
        override fun read(): String? = value
        override fun replace(refreshToken: String) {
            if (failOnReplace) error("storage unavailable")
            value = refreshToken
        }
        override fun clear() { value = null }
    }

    private class FakeRemote(private val response: AuthRemoteResult) : AuthRemoteDataSource {
        var calls = 0
        var lastLogoutAccessToken: String? = null
        override fun login(email: String, password: String): AuthRemoteResult = response.also { calls++ }
        override fun refresh(refreshToken: String): AuthRemoteResult = response.also { calls++ }
        override fun logout(accessToken: String?, refreshToken: String): AuthRemoteResult {
            calls++
            lastLogoutAccessToken = accessToken
            return response
        }
    }

    private class ThrowingRemote : AuthRemoteDataSource {
        override fun login(email: String, password: String): AuthRemoteResult = AuthRemoteResult.Unavailable
        override fun refresh(refreshToken: String): AuthRemoteResult = AuthRemoteResult.Unavailable
        override fun logout(accessToken: String?, refreshToken: String): AuthRemoteResult = error("network down")
    }
}
