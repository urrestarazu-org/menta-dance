package com.menta.android.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * OkHttp adapter for the Auth API. It intentionally never logs headers or bodies,
 * and the supplied [client] must not carry a logging interceptor.
 */
class OkHttpAuthRemoteDataSource(
    private val client: OkHttpClient,
    private val baseUrl: String,
) : AuthRemoteDataSource {
    override fun login(email: String, password: String): AuthRemoteResult = request(
        path = "/api/v1/auth/login",
        accessToken = null,
        refreshToken = null,
        body = json.encodeToString(LoginRequest.serializer(), LoginRequest(email, password))
            .toRequestBody(JSON_MEDIA_TYPE),
    )

    override fun refresh(refreshToken: String): AuthRemoteResult = request(
        path = "/api/v1/auth/refresh", accessToken = null, refreshToken = refreshToken, body = EMPTY_BODY,
    )

    override fun logout(accessToken: String?, refreshToken: String): AuthRemoteResult = request(
        path = "/api/v1/auth/logout", accessToken = accessToken, refreshToken = refreshToken, body = EMPTY_BODY,
    )

    private fun request(
        path: String,
        accessToken: String?,
        refreshToken: String?,
        body: RequestBody,
    ): AuthRemoteResult = try {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .post(body)
            .header("Accept", "application/json, application/problem+json")
            .apply {
                if (refreshToken != null) header(REFRESH_TOKEN_HEADER, refreshToken)
                if (accessToken != null) header("Authorization", "Bearer $accessToken")
            }
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) success(response) else problem(response)
        }
    } catch (_: Exception) {
        AuthRemoteResult.Unavailable
    }

    private fun success(response: Response): AuthRemoteResult {
        if (response.code == HTTP_NO_CONTENT) return AuthRemoteResult.Success(null, null, null)
        val token = json.decodeFromString(TokenResponse.serializer(), response.body.string())
        return AuthRemoteResult.Success(
            accessToken = token.accessToken?.takeIf(String::isNotBlank),
            refreshToken = response.header(REFRESH_TOKEN_HEADER)?.takeIf(String::isNotBlank),
            expiresInSeconds = token.expiresIn?.takeIf { it.isFinite() && it >= 0 }?.toLong(),
        )
    }

    private fun problem(response: Response): AuthRemoteResult {
        val code = response.problemCode()
        val retryAfter = if (response.code == HTTP_TOO_MANY_REQUESTS) {
            response.header("Retry-After")?.toLongOrNull()?.takeIf { it > 0 }
        } else null
        return AuthRemoteResult.Problem(code ?: "AUTH_REQUEST_FAILED", response.code, retryAfter)
    }

    private companion object {
        const val REFRESH_TOKEN_HEADER = "X-Refresh-Token"
        const val HTTP_NO_CONTENT = 204
        const val HTTP_TOO_MANY_REQUESTS = 429
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody()
        val json = Json { ignoreUnknownKeys = true }
    }
}
