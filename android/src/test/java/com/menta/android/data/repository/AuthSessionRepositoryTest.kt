package com.menta.android.data.repository

import com.menta.android.data.remote.AuthRemoteDataSource
import com.menta.android.data.remote.AuthRemoteResult
import com.menta.android.domain.model.SessionState
import com.menta.android.domain.repository.RefreshTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AuthSessionRepositoryTest {
    @Test
    fun refresh_replaces_the_persisted_refresh_only_after_a_valid_response() {
        val store = FakeRefreshTokenStore("old-refresh")
        val repository = AuthSessionRepository(store, FakeRemote(AuthRemoteResult.Success("access", "new-refresh", 900)))

        assertEquals(SessionState.Authenticated(900), repository.refresh())
        assertEquals("new-refresh", store.read())
    }

    @Test
    fun refresh_clears_local_state_when_rotated_refresh_cannot_be_persisted() {
        val store = FakeRefreshTokenStore("old-refresh", failOnReplace = true)
        val repository = AuthSessionRepository(store, FakeRemote(AuthRemoteResult.Success("access", "new-refresh", 900)))

        assertEquals(SessionState.Failed(com.menta.android.domain.model.AuthFailure("LOCAL_TOKEN_STORAGE_FAILED")), repository.refresh())
        assertNull(store.read())
    }

    @Test
    fun logout_clears_local_tokens_when_remote_revocation_throws() {
        val store = FakeRefreshTokenStore("refresh")
        val repository = AuthSessionRepository(store, ThrowingRemote())

        assertThrows(IllegalStateException::class.java) { repository.logout() }

        assertNull(store.read())
    }

    @Test
    fun refresh_preserves_problem_code_and_retry_after_without_exposing_response_detail() {
        val repository = AuthSessionRepository(
            FakeRefreshTokenStore("refresh"),
            FakeRemote(AuthRemoteResult.Problem("LOGIN_RATE_LIMITED", 45)),
        )

        assertEquals(
            SessionState.Failed(com.menta.android.domain.model.AuthFailure("LOGIN_RATE_LIMITED", 45)),
            repository.login("user@example.test", "password"),
        )
    }

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
        override fun login(email: String, password: String) = response
        override fun refresh(refreshToken: String) = response
        override fun logout(accessToken: String?, refreshToken: String) = response
    }

    private class ThrowingRemote : AuthRemoteDataSource {
        override fun login(email: String, password: String): AuthRemoteResult = AuthRemoteResult.Unavailable
        override fun refresh(refreshToken: String): AuthRemoteResult = AuthRemoteResult.Unavailable
        override fun logout(accessToken: String?, refreshToken: String): AuthRemoteResult = error("network down")
    }
}
