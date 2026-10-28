package com.menta.android.domain.model

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessCredentialTest {
    private val now = Instant.parse("2026-10-05T18:00:00Z")

    private fun credential(expiresInSeconds: Long, refreshAfterSeconds: Long = 30, value: String = "qr:abc") =
        AccessCredential(value, now.plusSeconds(expiresInSeconds), refreshAfterSeconds)

    @Test
    fun the_server_hint_wins_when_there_is_ample_time_to_expiry() {
        assertEquals(Duration.ofSeconds(30), credential(expiresInSeconds = 120).renewalDelay(now))
        assertEquals(Duration.ofSeconds(45), credential(expiresInSeconds = 120, refreshAfterSeconds = 45).renewalDelay(now))
    }

    @Test
    fun expiry_minus_the_margin_wins_when_it_is_earlier_than_the_hint() {
        assertEquals(Duration.ofSeconds(15), credential(expiresInSeconds = 20).renewalDelay(now))
        assertEquals(Duration.ofSeconds(10), credential(expiresInSeconds = 15).renewalDelay(now))
    }

    @Test
    fun an_expiry_already_past_yields_the_five_second_floor_not_zero() {
        assertEquals(Duration.ofSeconds(5), credential(expiresInSeconds = -60).renewalDelay(now))
        assertEquals(Duration.ofSeconds(5), credential(expiresInSeconds = 8).renewalDelay(now))
    }

    @Test
    fun a_tiny_server_hint_is_raised_to_the_floor() {
        assertEquals(Duration.ofSeconds(5), credential(expiresInSeconds = 120, refreshAfterSeconds = 1).renewalDelay(now))
    }

    @Test
    fun is_expired_at_is_true_from_the_expiry_instant_onwards() {
        val credential = credential(expiresInSeconds = 20)

        assertFalse(credential.isExpiredAt(now.plusSeconds(19)))
        assertTrue(credential.isExpiredAt(now.plusSeconds(20)))
        assertTrue(credential.isExpiredAt(now.plusSeconds(21)))
    }

    @Test
    fun to_string_omits_the_credential_value() {
        val text = credential(expiresInSeconds = 20, value = "qr:abc").toString()

        assertFalse(text.contains("qr:abc"))
        assertTrue(text.contains("expiresAt=${now.plusSeconds(20)}"))
        assertTrue(text.contains("refreshAfterSeconds=30"))
    }
}
