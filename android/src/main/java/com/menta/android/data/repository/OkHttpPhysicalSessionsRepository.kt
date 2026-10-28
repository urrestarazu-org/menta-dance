package com.menta.android.data.repository

import com.menta.android.data.remote.AccessQrResponseDto
import com.menta.android.data.remote.MySessionDto
import com.menta.android.data.remote.MySessionsResponseDto
import com.menta.android.data.remote.problemCode
import com.menta.android.domain.model.AccessCredential
import com.menta.android.domain.model.AccessDenial
import com.menta.android.domain.model.AccessQrResult
import com.menta.android.domain.model.MySession
import com.menta.android.domain.model.MySessionsResult
import com.menta.android.domain.repository.PhysicalSessionsRepository
import java.time.Instant
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * OkHttp adapter for the Physical API. The supplied [client] is the `@ApiHttpClient` stack
 * (Bearer injection, single refresh-and-retry) and must not carry a logging interceptor.
 * Every list failure collapses into [MySessionsResult.Unavailable]; access-QR failures are split
 * into denials (retrying cannot help) and [AccessQrResult.Unavailable] (retrying may).
 */
class OkHttpPhysicalSessionsRepository(
    private val client: OkHttpClient,
    baseUrl: String,
) : PhysicalSessionsRepository {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    override fun mySessions(): MySessionsResult = try {
        val request = Request.Builder()
            .url(base.newBuilder().addPathSegments(MY_SESSIONS_PATH).build())
            .get()
            .header("Accept", "application/json, application/problem+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) parse(response.body.string()) else MySessionsResult.Unavailable
        }
    } catch (_: Exception) {
        MySessionsResult.Unavailable
    }

    override fun issueAccessQr(sessionId: String): AccessQrResult = try {
        val request = Request.Builder()
            .url(base.newBuilder().addPathSegments(SESSIONS_PATH).addPathSegment(sessionId).addPathSegment("access-qr").build())
            .post(EMPTY_BODY)
            .header("Accept", "application/json, application/problem+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) issued(response.body.string()) else failure(response)
        }
    } catch (_: Exception) {
        AccessQrResult.Unavailable
    }

    private fun parse(body: String): MySessionsResult =
        MySessionsResult.Loaded(json.decodeFromString(MySessionsResponseDto.serializer(), body).sessions.map { it.toDomain() })

    private fun MySessionDto.toDomain() = MySession(
        sessionId = sessionId,
        courseName = courseName,
        instructorName = instructorName,
        scheduledAt = Instant.parse(scheduledAt),
        checkInOpen = checkInOpen,
    )

    private fun issued(body: String): AccessQrResult {
        val dto = json.decodeFromString(AccessQrResponseDto.serializer(), body)
        return AccessQrResult.Issued(AccessCredential(dto.qrCredentials, Instant.parse(dto.expiresAt), dto.refreshAfterSeconds))
    }

    /** Code first, then status: only statuses where retrying cannot help become a denial. */
    private fun failure(response: Response): AccessQrResult = when (response.code) {
        HTTP_NOT_FOUND -> AccessQrResult.Denied(AccessDenial.SESSION_NOT_FOUND)
        HTTP_FORBIDDEN -> AccessQrResult.Denied(response.problemCode().toDenial())
        in RETRYABLE_CLIENT_ERRORS -> AccessQrResult.Unavailable
        in HTTP_BAD_REQUEST..HTTP_LAST_CLIENT_ERROR -> AccessQrResult.Denied(AccessDenial.OTHER)
        else -> AccessQrResult.Unavailable
    }

    private fun String?.toDenial(): AccessDenial = when (this) {
        "OUTSIDE_CHECK_IN_WINDOW" -> AccessDenial.OUTSIDE_CHECK_IN_WINDOW
        "SESSION_CANCELLED" -> AccessDenial.SESSION_CANCELLED
        "CAPACITY_ASSIGNMENT_REQUIRED" -> AccessDenial.CAPACITY_ASSIGNMENT_REQUIRED
        else -> AccessDenial.OTHER
    }

    private companion object {
        const val SESSIONS_PATH = "api/v1/physical/sessions"
        const val MY_SESSIONS_PATH = "$SESSIONS_PATH/me"
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_LAST_CLIENT_ERROR = 499

        /** 401 is left to the session gate; 408 and 429 are transient. */
        val RETRYABLE_CLIENT_ERRORS = setOf(401, 408, 429)
        val EMPTY_BODY = ByteArray(0).toRequestBody()
        val json = Json { ignoreUnknownKeys = true }
    }
}
