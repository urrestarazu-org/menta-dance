package com.menta.android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire-level DTOs of the Auth API. They never leave the data layer. */
@Serializable
internal data class LoginRequest(val email: String, val password: String)

/**
 * `expires_in` arrives as a JSON number such as `900.0`, so it is decoded as a
 * [Double]; decoding it as a Long would fail.
 */
@Serializable
internal data class TokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("expires_in") val expiresIn: Double? = null,
)

/** Only the stable `code` is read from problem+json; `detail` is never surfaced. */
@Serializable
internal data class ProblemBody(val code: String? = null)
