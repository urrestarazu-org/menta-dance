package com.menta.android.data.remote

import com.menta.android.data.session.AccessTokenSource
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <access token>` to authenticated API calls. The credential
 * endpoints (login, refresh) never receive it, and a header set by the caller is left alone.
 */
class BearerTokenInterceptor(private val tokens: AccessTokenSource) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokens.currentAccessToken()
        val path = request.url.encodedPath
        if (token == null || request.header(AUTHORIZATION) != null || CREDENTIAL_PATHS.any(path::endsWith)) {
            return chain.proceed(request)
        }
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, "$BEARER_PREFIX$token").build())
    }

    internal companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        private val CREDENTIAL_PATHS = listOf("/auth/login", "/auth/refresh")
    }
}
