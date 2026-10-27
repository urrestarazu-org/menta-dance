package com.menta.android.data.repository

import com.menta.android.domain.model.MySession
import com.menta.android.domain.model.MySessionsResult
import java.time.Instant
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OkHttpPhysicalSessionsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: OkHttpPhysicalSessionsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = OkHttpPhysicalSessionsRepository(OkHttpClient(), server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun my_sessions_issues_a_get_with_the_json_and_problem_json_accept_header() {
        server.enqueue(okJson(sessionsBody()))

        repository.mySessions()

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/physical/sessions/me", request.url.encodedPath)
        assertEquals("application/json, application/problem+json", request.headers["Accept"])
    }

    @Test
    fun a_trailing_slash_in_the_base_url_does_not_duplicate_the_separator() {
        server.enqueue(okJson(sessionsBody()))
        val slashed = OkHttpPhysicalSessionsRepository(OkHttpClient(), server.url("/").toString().trimEnd('/') + "/")

        slashed.mySessions()

        assertEquals("/api/v1/physical/sessions/me", server.takeRequest().url.encodedPath)
    }

    @Test
    fun two_sessions_are_parsed_in_the_server_order_with_their_instants() {
        server.enqueue(
            okJson(
                sessionsBody(
                    sessionJson("s-1", "Salsa", "Ana", "2026-10-05T18:30:00Z", checkInOpen = true),
                    sessionJson("s-2", "Bachata", "Beto", "2026-10-06T20:00:00Z", checkInOpen = false),
                ),
            ),
        )

        val result = repository.mySessions()

        assertEquals(
            MySessionsResult.Loaded(
                listOf(
                    MySession("s-1", "Salsa", "Ana", Instant.parse("2026-10-05T18:30:00Z"), checkInOpen = true),
                    MySession("s-2", "Bachata", "Beto", Instant.parse("2026-10-06T20:00:00Z"), checkInOpen = false),
                ),
            ),
            result,
        )
    }

    @Test
    fun a_session_that_already_started_is_kept_like_any_other() {
        server.enqueue(
            okJson(
                sessionsBody(
                    sessionJson("started", "Tango", "Cora", "2020-01-01T08:00:00Z", checkInOpen = true),
                    sessionJson("later", "Salsa", "Ana", "2099-01-01T08:00:00Z", checkInOpen = false),
                ),
            ),
        )

        val result = repository.mySessions() as MySessionsResult.Loaded

        assertEquals(listOf("started", "later"), result.sessions.map { it.sessionId })
    }

    @Test
    fun an_empty_sessions_array_is_loaded_with_no_sessions() {
        server.enqueue(okJson(sessionsBody()))

        assertEquals(MySessionsResult.Loaded(emptyList()), repository.mySessions())
    }

    @Test
    fun unknown_fields_including_assignment_type_are_tolerated() {
        server.enqueue(
            okJson(
                """{"from":"2026-10-05T03:00:00Z","to":"2026-12-04T03:00:00Z","extra":1,"sessions":[
                    {"sessionId":"s-1","courseId":"c-1","courseName":"Salsa","instructorName":"Ana",
                    "scheduledAt":"2026-10-05T18:30:00Z","checkInOpen":false,"assignmentType":"MONTHLY","room":"A"}
                ]}""",
            ),
        )

        val result = repository.mySessions()

        assertEquals(
            MySessionsResult.Loaded(
                listOf(MySession("s-1", "Salsa", "Ana", Instant.parse("2026-10-05T18:30:00Z"), checkInOpen = false)),
            ),
            result,
        )
    }

    @Test
    fun a_malformed_body_is_unavailable() {
        server.enqueue(okJson("oops"))

        assertEquals(MySessionsResult.Unavailable, repository.mySessions())
    }

    @Test
    fun a_session_missing_a_required_field_is_unavailable() {
        server.enqueue(
            okJson(sessionsBody("""{"sessionId":"s-1","courseName":"Salsa","scheduledAt":"2026-10-05T18:30:00Z","checkInOpen":true}""")),
        )

        assertEquals(MySessionsResult.Unavailable, repository.mySessions())
    }

    @Test
    fun a_bad_instant_is_unavailable() {
        server.enqueue(okJson(sessionsBody(sessionJson("s-1", "Salsa", "Ana", "not-an-instant", checkInOpen = true))))

        assertEquals(MySessionsResult.Unavailable, repository.mySessions())
    }

    @Test
    fun a_server_error_is_unavailable() {
        server.enqueue(MockResponse.Builder().code(500).body("boom").build())

        assertEquals(MySessionsResult.Unavailable, repository.mySessions())
    }

    @Test
    fun an_unauthorized_answer_is_unavailable_and_left_to_the_session_gate() {
        server.enqueue(MockResponse.Builder().code(401).build())

        assertEquals(MySessionsResult.Unavailable, repository.mySessions())
    }

    @Test
    fun an_io_failure_is_unavailable() {
        val deadUrl = server.url("/").toString()
        server.close()
        val offline = OkHttpPhysicalSessionsRepository(OkHttpClient(), deadUrl)

        assertEquals(MySessionsResult.Unavailable, offline.mySessions())
    }

    private fun okJson(body: String) =
        MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build()

    private fun sessionsBody(vararg sessions: String) =
        """{"from":"2026-10-05T03:00:00Z","to":"2026-12-04T03:00:00Z","sessions":[${sessions.joinToString(",")}]}"""

    private fun sessionJson(id: String, course: String, instructor: String, scheduledAt: String, checkInOpen: Boolean) =
        """{"sessionId":"$id","courseId":"c-$id","courseName":"$course","instructorName":"$instructor",""" +
            """"scheduledAt":"$scheduledAt","checkInOpen":$checkInOpen,"assignmentType":"MONTHLY"}"""
}
