package com.menta.android.data.repository

import com.menta.android.data.remote.MySessionDto
import com.menta.android.data.remote.MySessionsResponseDto
import com.menta.android.domain.model.MySession
import com.menta.android.domain.model.MySessionsResult
import com.menta.android.domain.repository.PhysicalSessionsRepository
import java.time.Instant
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OkHttp adapter for the Physical API. The supplied [client] is the `@ApiHttpClient` stack
 * (Bearer injection, single refresh-and-retry) and must not carry a logging interceptor.
 * Every failure collapses into [MySessionsResult.Unavailable].
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

    private fun parse(body: String): MySessionsResult =
        MySessionsResult.Loaded(json.decodeFromString(MySessionsResponseDto.serializer(), body).sessions.map { it.toDomain() })

    private fun MySessionDto.toDomain() = MySession(
        sessionId = sessionId,
        courseName = courseName,
        instructorName = instructorName,
        scheduledAt = Instant.parse(scheduledAt),
        checkInOpen = checkInOpen,
    )

    private companion object {
        const val MY_SESSIONS_PATH = "api/v1/physical/sessions/me"
        val json = Json { ignoreUnknownKeys = true }
    }
}
