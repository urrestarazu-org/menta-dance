package com.menta.android.data.repository

import com.menta.android.data.remote.AttendanceHistoryResponseDto
import com.menta.android.data.remote.AttendanceSessionDto
import com.menta.android.domain.model.AttendanceEntry
import com.menta.android.domain.model.AttendanceHistoryResult
import com.menta.android.domain.model.AttendanceOrigin
import com.menta.android.domain.repository.AttendanceHistoryRepository
import com.menta.android.domain.time.DeviceZone
import java.time.Instant
import java.time.YearMonth
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OkHttp adapter for the monthly attendance history. The supplied [client] is the `@ApiHttpClient`
 * stack (Bearer injection, single refresh-and-retry) and must not carry a logging interceptor.
 * Every failure collapses into [AttendanceHistoryResult.Unavailable].
 *
 * The API takes a half-open UTC range `[from, to)`; the month is cut in the [deviceZone] (ADR-0048).
 */
class OkHttpAttendanceHistoryRepository(
    private val client: OkHttpClient,
    baseUrl: String,
    private val deviceZone: DeviceZone,
) : AttendanceHistoryRepository {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    override fun monthlyAttendance(month: YearMonth): AttendanceHistoryResult = try {
        val zone = deviceZone.current()
        val request = Request.Builder()
            .url(
                base.newBuilder()
                    .addPathSegments(ATTENDANCE_PATH)
                    .addQueryParameter("from", month.atDay(1).atStartOfDay(zone).toInstant().toString())
                    .addQueryParameter("to", month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toString())
                    .build(),
            )
            .get()
            .header("Accept", "application/json, application/problem+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) parse(response.body.string()) else AttendanceHistoryResult.Unavailable
        }
    } catch (_: Exception) {
        AttendanceHistoryResult.Unavailable
    }

    private fun parse(body: String): AttendanceHistoryResult = AttendanceHistoryResult.Loaded(
        json.decodeFromString(AttendanceHistoryResponseDto.serializer(), body).sessions.mapNotNull { it.toDomain() },
    )

    /** A row without `recordedAt` is not an attendance (it is never expected here): skipped, not an error. */
    private fun AttendanceSessionDto.toDomain(): AttendanceEntry? {
        val recorded = recordedAt ?: return null
        return AttendanceEntry(
            sessionId = sessionId,
            courseName = courseName,
            instructorName = instructorName,
            scheduledAt = Instant.parse(scheduledAt),
            recordedAt = Instant.parse(recorded),
            origin = kind.toOrigin(),
        )
    }

    private fun String?.toOrigin(): AttendanceOrigin = when (this) {
        "QR" -> AttendanceOrigin.QR
        "MANUAL" -> AttendanceOrigin.MANUAL
        else -> AttendanceOrigin.UNKNOWN
    }

    private companion object {
        const val ATTENDANCE_PATH = "api/v1/physical/attendance/me"
        val json = Json { ignoreUnknownKeys = true }
    }
}
