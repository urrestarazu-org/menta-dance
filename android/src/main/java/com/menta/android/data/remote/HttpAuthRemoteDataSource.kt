package com.menta.android.data.remote

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** HTTP adapter for the Auth API. It intentionally never logs headers or bodies. */
class HttpAuthRemoteDataSource(private val baseUrl: String) : AuthRemoteDataSource {
    override fun login(email: String, password: String): AuthRemoteResult = request(
        path = "/api/v1/auth/login",
        accessToken = null,
        refreshToken = null,
        requestBody = JSONObject().put("email", email).put("password", password).toString(),
    )

    override fun refresh(refreshToken: String): AuthRemoteResult = request(
        path = "/api/v1/auth/refresh", accessToken = null, refreshToken = refreshToken, requestBody = null,
    )

    override fun logout(accessToken: String?, refreshToken: String): AuthRemoteResult = request(
        path = "/api/v1/auth/logout", accessToken = accessToken, refreshToken = refreshToken, requestBody = null,
    )

    private fun request(
        path: String,
        accessToken: String?,
        refreshToken: String?,
        requestBody: String?,
    ): AuthRemoteResult = try {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.setRequestProperty("Accept", "application/json, application/problem+json")
        if (refreshToken != null) connection.setRequestProperty(REFRESH_TOKEN_HEADER, refreshToken)
        if (accessToken != null) connection.setRequestProperty("Authorization", "Bearer $accessToken")
        if (requestBody != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter().use { it.write(requestBody) }
        }
        try {
            val status = connection.responseCode
            if (status in 200..299) success(connection) else problem(connection, status)
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        AuthRemoteResult.Unavailable
    }

    private fun success(connection: HttpURLConnection): AuthRemoteResult {
        if (connection.responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
            return AuthRemoteResult.Success(null, null, null)
        }
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        return AuthRemoteResult.Success(
            accessToken = json.optString("access_token").takeIf(String::isNotBlank),
            refreshToken = connection.getHeaderField(REFRESH_TOKEN_HEADER)?.takeIf(String::isNotBlank),
            expiresInSeconds = json.optLong("expires_in", -1).takeIf { it >= 0 },
        )
    }

    private fun problem(connection: HttpURLConnection, status: Int): AuthRemoteResult {
        val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val code = if (connection.contentType.orEmpty().startsWith("application/problem+json", true)) {
            runCatching { JSONObject(body).optString("code") }.getOrNull()?.takeIf(String::isNotBlank)
        } else null
        val retryAfter = if (status == HTTP_TOO_MANY_REQUESTS) {
            connection.getHeaderField("Retry-After")?.toLongOrNull()?.takeIf { it > 0 }
        } else null
        return AuthRemoteResult.Problem(code ?: "AUTH_REQUEST_FAILED", retryAfter)
    }

    private companion object {
        const val REFRESH_TOKEN_HEADER = "X-Refresh-Token"
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val TIMEOUT_MILLIS = 10_000
    }
}
