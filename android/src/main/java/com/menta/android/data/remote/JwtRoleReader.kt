package com.menta.android.data.remote

import com.menta.android.domain.model.Role
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads the `role` claim from an Auth API access token, mirroring the BFF's
 * `JwtAccessTokenClaimsReader`.
 *
 * The signature is NOT verified: the role only gates the UX, and the API
 * authorizes every call against the verified token. Any failure yields `null`,
 * which callers must treat as "not a student". The token is never logged and
 * never placed in an exception message.
 */
@OptIn(ExperimentalEncodingApi::class)
object JwtRoleReader {
    private const val ROLE_CLAIM = "role"
    private val ROLE_NAME = Regex("[A-Z][A-Z_]*")
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    fun roleOf(accessToken: String): Role? {
        val parts = accessToken.split('.')
        if (parts.size != 3) return null
        return runCatching {
            val payload = Json.parseToJsonElement(base64Url.decode(parts[1]).decodeToString()) as? JsonObject
            val claim = payload?.get(ROLE_CLAIM) as? JsonPrimitive
            claim?.takeIf { it.isString }?.content?.takeIf(ROLE_NAME::matches)
        }.getOrNull()?.let { name -> Role.entries.firstOrNull { it.name == name } }
    }
}
