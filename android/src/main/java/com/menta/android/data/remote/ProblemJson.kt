package com.menta.android.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Response

private const val PROBLEM_JSON = "application/problem+json"
private val problemJson = Json { ignoreUnknownKeys = true }

/**
 * The stable `code` of an RFC 7807 body, or null when the response is not `application/problem+json`,
 * the body is unreadable or the code is blank. Consumes the body; `detail` is never surfaced.
 */
internal fun Response.problemCode(): String? {
    if (!header("Content-Type").orEmpty().startsWith(PROBLEM_JSON, ignoreCase = true)) return null
    return runCatching { problemJson.decodeFromString(ProblemBody.serializer(), body.string()).code }
        .getOrNull()?.takeIf(String::isNotBlank)
}
