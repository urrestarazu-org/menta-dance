package com.menta.android.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
