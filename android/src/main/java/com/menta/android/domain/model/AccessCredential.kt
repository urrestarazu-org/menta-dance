package com.menta.android.domain.model

import java.time.Duration
import java.time.Instant

/**
 * Short-lived access QR credential. [value] is opaque: it is never parsed and never printed,
 * so [toString] omits it.
 */
data class AccessCredential(
    val value: String,
    val expiresAt: Instant,
    val refreshAfterSeconds: Long,
) {
    /**
     * How long to wait before asking for the next credential: the server hint, capped so the
     * renewal lands [EXPIRY_MARGIN] before expiry, and never below [MIN_RENEWAL_DELAY] (clock
     * skew must not produce a hot loop).
     */
    fun renewalDelay(now: Instant): Duration {
        val untilExpiry = Duration.between(now, expiresAt).minus(EXPIRY_MARGIN)
        val delay = minOf(Duration.ofSeconds(refreshAfterSeconds), untilExpiry)
        return maxOf(MIN_RENEWAL_DELAY, delay)
    }

    fun isExpiredAt(now: Instant): Boolean = !now.isBefore(expiresAt)

    override fun toString() = "AccessCredential(expiresAt=$expiresAt, refreshAfterSeconds=$refreshAfterSeconds)"

    private companion object {
        val EXPIRY_MARGIN: Duration = Duration.ofSeconds(5)
        val MIN_RENEWAL_DELAY: Duration = Duration.ofSeconds(5)
    }
}
