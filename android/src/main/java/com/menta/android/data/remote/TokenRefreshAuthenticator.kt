package com.menta.android.data.remote

import com.menta.android.data.remote.BearerTokenInterceptor.Companion.AUTHORIZATION
import com.menta.android.data.remote.BearerTokenInterceptor.Companion.BEARER_PREFIX
import com.menta.android.data.session.AccessTokenSource
import com.menta.android.data.session.AccessTokenSource.Renewal
import java.io.IOException
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Reacts to a 401 by renewing the session once (single-flight, see [AccessTokenSource]) and
 * retrying the request once with the new token. A 401 that survives that retry means the
 * session is dead: it is wiped and signalled, and the caller sees the 401.
 *
 * OkHttp calls this on its own threads and expects it to block.
 */
class TokenRefreshAuthenticator(private val tokens: AccessTokenSource) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val staleToken = response.request.header(AUTHORIZATION)
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?: return null // Nothing was authenticated, so there is nothing to renew.

        if (response.hasPriorUnauthorized()) {
            tokens.expireSession()
            return null
        }
        return when (val renewal = tokens.renewAfterUnauthorized(staleToken)) {
            is Renewal.Renewed ->
                response.request.newBuilder().header(AUTHORIZATION, "$BEARER_PREFIX${renewal.accessToken}").build()
            Renewal.Rejected -> null // Already wiped and signalled by the token source.
            Renewal.Unavailable -> {
                response.close()
                throw RefreshUnavailableException()
            }
        }
    }

    /** Only an earlier 401 counts: a redirect also leaves a prior response and must not look like a retry. */
    private fun Response.hasPriorUnauthorized(): Boolean =
        generateSequence(priorResponse) { it.priorResponse }.any { it.code == HTTP_UNAUTHORIZED }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}

/** The session could not be renewed because the API was unreachable; the stored token was kept. */
class RefreshUnavailableException : IOException("Session refresh unavailable")
