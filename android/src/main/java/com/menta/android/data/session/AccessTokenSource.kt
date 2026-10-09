package com.menta.android.data.session

/**
 * Data-internal port the HTTP stack uses to read and renew the in-memory access token.
 * Implementations are thread-safe; calls arrive from OkHttp dispatcher threads.
 */
interface AccessTokenSource {
    /** The access token held right now, or null when signed out. */
    fun currentAccessToken(): String?

    /**
     * Renews the session after a 401 produced by [staleAccessToken]. Concurrent callers share
     * one remote refresh, because a rotated refresh token must never be presented twice.
     */
    fun renewAfterUnauthorized(staleAccessToken: String): Renewal

    /** Wipes the local session and signals that it expired (a 401 that renewal could not fix). */
    fun expireSession()

    sealed interface Renewal {
        /** A usable access token; [toString] deliberately omits it. */
        data class Renewed(val accessToken: String) : Renewal {
            override fun toString(): String = "Renewed"
        }

        /** The server refused the refresh token. The session is already wiped and signalled. */
        data object Rejected : Renewal

        /** The refresh could not be completed (offline, 503). The stored token is kept. */
        data object Unavailable : Renewal
    }
}
