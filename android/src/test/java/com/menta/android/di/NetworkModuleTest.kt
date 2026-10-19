package com.menta.android.di

import com.menta.android.data.remote.BearerTokenInterceptor
import com.menta.android.data.remote.TokenRefreshAuthenticator
import com.menta.android.data.session.AccessTokenSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkModuleTest {
    private val client = NetworkModule.authHttpClient()

    @Test
    fun auth_client_never_replays_a_request_after_a_connection_failure() {
        assertFalse(client.retryOnConnectionFailure)
    }

    @Test
    fun auth_client_uses_ten_second_timeouts() {
        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(10_000, client.readTimeoutMillis)
        assertEquals(10_000, client.writeTimeoutMillis)
    }

    @Test
    fun auth_client_has_no_interceptors_so_tokens_cannot_be_logged_or_injected() {
        assertTrue(client.interceptors.isEmpty())
        assertTrue(client.networkInterceptors.isEmpty())
    }

    private val apiClient = NetworkModule.apiHttpClient(client, NoSession)

    @Test
    fun api_client_adds_only_the_bearer_interceptor_and_the_refresh_authenticator() {
        assertEquals(listOf(BearerTokenInterceptor::class.java), apiClient.interceptors.map { it.javaClass })
        assertTrue(apiClient.networkInterceptors.isEmpty())
        assertEquals(TokenRefreshAuthenticator::class.java, apiClient.authenticator.javaClass)
    }

    @Test
    fun api_client_keeps_the_no_replay_rule_and_the_ten_second_timeouts() {
        assertFalse(apiClient.retryOnConnectionFailure)
        assertEquals(10_000, apiClient.connectTimeoutMillis)
        assertEquals(10_000, apiClient.readTimeoutMillis)
        assertEquals(10_000, apiClient.writeTimeoutMillis)
    }

    @Test
    fun api_client_shares_the_connection_pool_with_the_auth_client() {
        assertSame(client.connectionPool, apiClient.connectionPool)
    }

    @Test
    fun auth_client_still_has_no_authenticator_so_a_refresh_can_never_recurse() {
        assertSame(okhttp3.Authenticator.NONE, client.authenticator)
    }

    private object NoSession : AccessTokenSource {
        override fun currentAccessToken(): String? = null
        override fun renewAfterUnauthorized(staleAccessToken: String) = AccessTokenSource.Renewal.Rejected
        override fun expireSession() = Unit
    }
}
