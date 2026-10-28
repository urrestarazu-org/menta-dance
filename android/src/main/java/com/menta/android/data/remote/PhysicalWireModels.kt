package com.menta.android.data.remote

import kotlinx.serialization.Serializable

/**
 * Wire-level DTOs of the Physical API. They never leave the data layer. Fields the app does
 * not use (`from`, `to`, `courseId`, `assignmentType`, ...) are skipped through `ignoreUnknownKeys`.
 */
@Serializable
internal data class MySessionsResponseDto(val sessions: List<MySessionDto>)

@Serializable
internal data class MySessionDto(
    val sessionId: String,
    val courseName: String,
    val instructorName: String,
    val scheduledAt: String,
    val checkInOpen: Boolean,
)

/** `qrCredentials` is a secret: [toString] masks it so it cannot leak through logs or crash reports. */
@Serializable
internal data class AccessQrResponseDto(
    val qrCredentials: String,
    val expiresAt: String,
    val refreshAfterSeconds: Long,
) {
    override fun toString() = "AccessQrResponseDto(expiresAt=$expiresAt, refreshAfterSeconds=$refreshAfterSeconds)"
}
