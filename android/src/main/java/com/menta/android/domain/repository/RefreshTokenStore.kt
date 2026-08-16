package com.menta.android.domain.repository

/**
 * Durable custody of the opaque refresh credential on the device.
 *
 * Implementations keep the token encrypted at rest and never expose it through
 * logs, crash reports or analytics. The access token is deliberately absent
 * from this contract: it stays in process memory only.
 */
interface RefreshTokenStore {

    /**
     * @return the stored refresh token, or `null` when there is none or the
     *     stored value can no longer be decrypted. An unreadable value is
     *     treated as no session rather than as an error to surface.
     */
    fun read(): String?

    /**
     * Replaces the stored refresh token, leaving no recoverable copy of the
     * previous value.
     */
    fun replace(refreshToken: String)

    /**
     * Removes the stored token. Must succeed locally even when server-side
     * revocation failed.
     */
    fun clear()
}
