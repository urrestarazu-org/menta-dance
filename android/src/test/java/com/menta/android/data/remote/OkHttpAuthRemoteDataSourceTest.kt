package com.menta.android.data.remote

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OkHttpAuthRemoteDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var remote: OkHttpAuthRemoteDataSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        remote = OkHttpAuthRemoteDataSource(OkHttpClient(), server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun login_posts_json_credentials_without_authorization_and_decodes_the_token_pair() {
        server.enqueue(tokenResponse(accessToken = "access-1", refreshToken = "refresh-1", expiresIn = "900.0"))

        val result = remote.login("alumno@menta.test", "s3cret")

        assertEquals(AuthRemoteResult.Success("access-1", "refresh-1", 900L), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/login", request.url.encodedPath)
        assertEquals("{\"email\":\"alumno@menta.test\",\"password\":\"s3cret\"}", request.body?.utf8())
        assertEquals("application/json", request.headers["Content-Type"]?.substringBefore(";"))
        assertEquals("application/json, application/problem+json", request.headers["Accept"])
        assertNull(request.headers["Authorization"])
        assertNull(request.headers["X-Refresh-Token"])
    }

    @Test
    fun login_decodes_an_integer_expires_in_as_well_as_a_decimal_one() {
        server.enqueue(tokenResponse(accessToken = "access-2", refreshToken = "refresh-2", expiresIn = "1800"))

        assertEquals(AuthRemoteResult.Success("access-2", "refresh-2", 1800L), remote.login("a@b.test", "pw"))
    }

    @Test
    fun login_escapes_special_characters_in_credentials() {
        server.enqueue(tokenResponse("access", "refresh", "900.0"))

        remote.login("a@b.test", "pa\"ss\\word")

        assertEquals("{\"email\":\"a@b.test\",\"password\":\"pa\\\"ss\\\\word\"}", server.takeRequest().body?.utf8())
    }

    @Test
    fun refresh_sends_the_refresh_token_header_with_no_body_and_no_authorization() {
        server.enqueue(tokenResponse("access-3", "refresh-3", "900.0"))

        val result = remote.refresh("stored-refresh")

        assertEquals(AuthRemoteResult.Success("access-3", "refresh-3", 900L), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/auth/refresh", request.url.encodedPath)
        assertEquals("stored-refresh", request.headers["X-Refresh-Token"])
        assertNull(request.headers["Authorization"])
        assertEquals(0L, request.bodySize)
    }

    @Test
    fun logout_sends_bearer_and_refresh_headers_and_accepts_no_content() {
        server.enqueue(MockResponse.Builder().code(204).build())

        val result = remote.logout("access-4", "refresh-4")

        assertEquals(AuthRemoteResult.Success(null, null, null), result)
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/logout", request.url.encodedPath)
        assertEquals("Bearer access-4", request.headers["Authorization"])
        assertEquals("refresh-4", request.headers["X-Refresh-Token"])
    }

    @Test
    fun logout_without_access_token_sends_no_authorization_header() {
        server.enqueue(MockResponse.Builder().code(204).build())

        remote.logout(null, "refresh-5")

        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun problem_json_exposes_the_stable_code_and_the_http_status() {
        server.enqueue(problemResponse(401, "{\"code\":\"INVALID_CREDENTIALS\",\"detail\":\"secret detail\"}"))

        assertEquals(AuthRemoteResult.Problem("INVALID_CREDENTIALS", 401), remote.login("a@b.test", "bad"))
    }

    @Test
    fun problem_json_on_another_status_keeps_that_status() {
        server.enqueue(problemResponse(423, "{\"code\":\"USER_LOCKED\"}"))

        assertEquals(AuthRemoteResult.Problem("USER_LOCKED", 423), remote.login("a@b.test", "pw"))
    }

    @Test
    fun rate_limited_problem_carries_the_retry_after_seconds() {
        server.enqueue(
            problemResponse(429, "{\"code\":\"LOGIN_RATE_LIMITED\"}", extraHeaders = mapOf("Retry-After" to "30")),
        )

        assertEquals(AuthRemoteResult.Problem("LOGIN_RATE_LIMITED", 429, 30L), remote.login("a@b.test", "pw"))
    }

    @Test
    fun retry_after_is_ignored_for_statuses_other_than_429() {
        server.enqueue(problemResponse(503, "{\"code\":\"AUTH_DEGRADED\"}", extraHeaders = mapOf("Retry-After" to "30")))

        assertEquals(AuthRemoteResult.Problem("AUTH_DEGRADED", 503), remote.login("a@b.test", "pw"))
    }

    @Test
    fun a_non_problem_json_error_falls_back_to_the_generic_code() {
        server.enqueue(
            MockResponse.Builder().code(500).addHeader("Content-Type", "text/html").body("<html>boom</html>").build(),
        )

        assertEquals(AuthRemoteResult.Problem("AUTH_REQUEST_FAILED", 500), remote.login("a@b.test", "pw"))
    }

    @Test
    fun a_problem_json_body_that_cannot_be_parsed_falls_back_to_the_generic_code() {
        server.enqueue(problemResponse(400, "not-json"))

        assertEquals(AuthRemoteResult.Problem("AUTH_REQUEST_FAILED", 400), remote.refresh("stored"))
    }

    @Test
    fun a_missing_refresh_header_yields_a_success_without_refresh_token() {
        server.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                .body("{\"access_token\":\"a\",\"token_type\":\"Bearer\",\"expires_in\":900.0}").build(),
        )

        assertEquals(AuthRemoteResult.Success("a", null, 900L), remote.refresh("stored"))
    }

    @Test
    fun a_malformed_success_body_is_reported_as_unavailable() {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body("oops").build())

        assertEquals(AuthRemoteResult.Unavailable, remote.login("a@b.test", "pw"))
    }

    @Test
    fun a_network_failure_is_reported_as_unavailable() {
        val deadUrl = server.url("/").toString()
        server.close()
        val offline = OkHttpAuthRemoteDataSource(OkHttpClient(), deadUrl)

        assertEquals(AuthRemoteResult.Unavailable, offline.refresh("stored"))
    }

    @Test
    fun a_trailing_slash_in_the_base_url_does_not_duplicate_the_separator() {
        server.enqueue(MockResponse.Builder().code(204).build())

        remote.logout("a", "r")

        assertEquals("/api/v1/auth/logout", server.takeRequest().url.encodedPath)
    }

    private fun tokenResponse(accessToken: String, refreshToken: String, expiresIn: String) =
        MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Refresh-Token", refreshToken)
            .body("{\"access_token\":\"$accessToken\",\"token_type\":\"Bearer\",\"expires_in\":$expiresIn}")
            .build()

    private fun problemResponse(status: Int, body: String, extraHeaders: Map<String, String> = emptyMap()) =
        MockResponse.Builder()
            .code(status)
            .addHeader("Content-Type", "application/problem+json")
            .apply { extraHeaders.forEach { (name, value) -> addHeader(name, value) } }
            .body(body)
            .build()
}
