package com.menta.android.data.repository

import com.menta.android.domain.model.AttendanceEntry
import com.menta.android.domain.model.AttendanceHistoryResult
import com.menta.android.domain.model.AttendanceOrigin
import java.time.Instant
import java.time.YearMonth
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OkHttpAttendanceHistoryRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: OkHttpAttendanceHistoryRepository

    private val september = YearMonth.of(2026, 9)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = OkHttpAttendanceHistoryRepository(OkHttpClient(), server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun the_request_is_a_get_with_the_month_query_and_the_accept_header_and_nothing_else() {
        server.enqueue(okJson(historyBody()))

        repository.monthlyAttendance(september)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/physical/attendance/me", request.url.encodedPath)
        assertEquals("month=2026-09", request.url.encodedQuery)
        assertNull(request.url.queryParameter("studentId"))
        assertNull(request.url.queryParameter("includeAbsent"))
        assertEquals("application/json, application/problem+json", request.headers["Accept"])
    }

    @Test
    fun the_month_query_follows_the_requested_month() {
        server.enqueue(okJson(historyBody()))

        repository.monthlyAttendance(YearMonth.of(2024, 1))

        assertEquals("month=2024-01", server.takeRequest().url.encodedQuery)
    }

    @Test
    fun a_trailing_slash_in_the_base_url_does_not_duplicate_the_separator() {
        server.enqueue(okJson(historyBody()))
        val slashed = OkHttpAttendanceHistoryRepository(OkHttpClient(), server.url("/").toString().trimEnd('/') + "/")

        slashed.monthlyAttendance(september)

        assertEquals("/api/v1/physical/attendance/me", server.takeRequest().url.encodedPath)
    }

    @Test
    fun twelve_attended_rows_arrive_in_one_response_and_one_request() {
        val rows = (1..12).map { day ->
            val date = "2026-09-%02d".format(day)
            rowJson("s-$day", "${date}T22:00:00Z", "${date}T22:02:00Z", "QR")
        }
        server.enqueue(okJson(historyBody(*rows.toTypedArray())))

        val result = repository.monthlyAttendance(september) as AttendanceHistoryResult.Loaded

        assertEquals(12, result.entries.size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun a_qr_row_is_parsed_into_an_entry_with_its_instants_and_origin() {
        server.enqueue(okJson(historyBody(rowJson("s-1", "2026-09-03T22:00:00Z", "2026-09-03T22:02:00Z", "QR"))))

        val result = repository.monthlyAttendance(september)

        assertEquals(
            AttendanceHistoryResult.Loaded(
                listOf(
                    AttendanceEntry(
                        sessionId = "s-1",
                        courseName = "Salsa",
                        instructorName = "Ana",
                        scheduledAt = Instant.parse("2026-09-03T22:00:00Z"),
                        recordedAt = Instant.parse("2026-09-03T22:02:00Z"),
                        origin = AttendanceOrigin.QR,
                    ),
                ),
            ),
            result,
        )
    }

    @Test
    fun a_manual_row_maps_to_the_manual_origin() {
        server.enqueue(okJson(historyBody(rowJson("s-1", "2026-09-03T22:00:00Z", "2026-09-03T22:02:00Z", "MANUAL"))))

        val result = repository.monthlyAttendance(september) as AttendanceHistoryResult.Loaded

        assertEquals(AttendanceOrigin.MANUAL, result.entries.single().origin)
    }

    @Test
    fun a_null_missing_or_unrecognised_kind_is_unknown_and_parsing_still_succeeds() {
        server.enqueue(
            okJson(
                historyBody(
                    rowJson("null", "2026-09-03T22:00:00Z", "2026-09-03T22:02:00Z", kind = null),
                    rowJson("missing", "2026-09-04T22:00:00Z", "2026-09-04T22:02:00Z", kind = ABSENT_KEY),
                    rowJson("nfc", "2026-09-05T22:00:00Z", "2026-09-05T22:02:00Z", "NFC"),
                    rowJson("qr", "2026-09-06T22:00:00Z", "2026-09-06T22:02:00Z", "QR"),
                ),
            ),
        )

        val result = repository.monthlyAttendance(september) as AttendanceHistoryResult.Loaded

        assertEquals(
            mapOf(
                "null" to AttendanceOrigin.UNKNOWN,
                "missing" to AttendanceOrigin.UNKNOWN,
                "nfc" to AttendanceOrigin.UNKNOWN,
                "qr" to AttendanceOrigin.QR,
            ),
            result.entries.associate { it.sessionId to it.origin },
        )
    }

    @Test
    fun rows_keep_the_server_order_even_when_it_is_not_chronological() {
        server.enqueue(
            okJson(
                historyBody(
                    rowJson("b", "2026-09-20T22:00:00Z", "2026-09-20T22:02:00Z", "QR"),
                    rowJson("a", "2026-09-03T22:00:00Z", "2026-09-03T22:02:00Z", "QR"),
                    rowJson("c", "2026-10-01T02:30:00Z", "2026-10-01T02:31:00Z", "MANUAL"),
                ),
            ),
        )

        val result = repository.monthlyAttendance(september) as AttendanceHistoryResult.Loaded

        assertEquals(listOf("b", "a", "c"), result.entries.map { it.sessionId })
    }

    @Test
    fun a_row_without_a_recorded_instant_is_skipped_and_the_others_are_kept() {
        server.enqueue(
            okJson(
                historyBody(
                    rowJson("attended", "2026-09-03T22:00:00Z", "2026-09-03T22:02:00Z", "QR"),
                    rowJson("absent", "2026-09-04T22:00:00Z", recordedAt = null, kind = null),
                    rowJson("later", "2026-09-05T22:00:00Z", "2026-09-05T22:02:00Z", "MANUAL"),
                ),
            ),
        )

        val result = repository.monthlyAttendance(september) as AttendanceHistoryResult.Loaded

        assertEquals(listOf("attended", "later"), result.entries.map { it.sessionId })
    }

    @Test
    fun an_empty_sessions_array_is_loaded_with_no_entries() {
        server.enqueue(okJson(historyBody()))

        assertEquals(AttendanceHistoryResult.Loaded(emptyList()), repository.monthlyAttendance(september))
    }

    @Test
    fun aggregates_and_unknown_fields_are_tolerated() {
        server.enqueue(
            okJson(
                """{"month":"2026-09","scheduledSessionCount":3,"attended":1,"absent":2,"attendanceRate":33.33,"extra":true,"sessions":[
                    {"sessionId":"s-1","courseId":"c-1","courseName":"Salsa","instructorName":"Ana",
                    "scheduledAt":"2026-09-03T22:00:00Z","status":"ATTENDED","recordedAt":"2026-09-03T22:02:00Z","kind":"QR","room":"A"}
                ]}""",
            ),
        )

        val result = repository.monthlyAttendance(september) as AttendanceHistoryResult.Loaded

        assertEquals(listOf("s-1"), result.entries.map { it.sessionId })
    }

    @Test
    fun a_malformed_body_is_unavailable() {
        server.enqueue(okJson("oops"))

        assertEquals(AttendanceHistoryResult.Unavailable, repository.monthlyAttendance(september))
    }

    @Test
    fun a_row_missing_a_required_field_is_unavailable() {
        server.enqueue(
            okJson(historyBody("""{"sessionId":"s-1","courseName":"Salsa","scheduledAt":"2026-09-03T22:00:00Z","recordedAt":"2026-09-03T22:02:00Z"}""")),
        )

        assertEquals(AttendanceHistoryResult.Unavailable, repository.monthlyAttendance(september))
    }

    @Test
    fun a_malformed_instant_is_unavailable() {
        server.enqueue(okJson(historyBody(rowJson("s-1", "not-an-instant", "2026-09-03T22:02:00Z", "QR"))))
        server.enqueue(okJson(historyBody(rowJson("s-1", "2026-09-03T22:00:00Z", "not-an-instant", "QR"))))

        assertEquals(AttendanceHistoryResult.Unavailable, repository.monthlyAttendance(september))
        assertEquals(AttendanceHistoryResult.Unavailable, repository.monthlyAttendance(september))
    }

    @Test
    fun every_failing_status_is_unavailable() {
        listOf(400, 401, 500).forEach { status ->
            server.enqueue(MockResponse.Builder().code(status).body("boom").build())

            assertEquals("status $status", AttendanceHistoryResult.Unavailable, repository.monthlyAttendance(september))
        }
    }

    @Test
    fun an_io_failure_is_unavailable() {
        val deadUrl = server.url("/").toString()
        server.close()
        val offline = OkHttpAttendanceHistoryRepository(OkHttpClient(), deadUrl)

        assertEquals(AttendanceHistoryResult.Unavailable, offline.monthlyAttendance(september))
    }

    private fun okJson(body: String) =
        MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build()

    private fun historyBody(vararg sessions: String) =
        """{"month":"2026-09","scheduledSessionCount":${sessions.size},"attended":${sessions.size},"absent":0,"attendanceRate":100.0,""" +
            """"sessions":[${sessions.joinToString(",")}]}"""

    /** [kind] and [recordedAt] render as JSON `null` when null; [ABSENT_KEY] omits `kind` entirely. */
    private fun rowJson(
        id: String,
        scheduledAt: String,
        recordedAt: String?,
        kind: String? = null,
    ): String {
        val recorded = recordedAt?.let { "\"$it\"" } ?: "null"
        val kindField = when (kind) {
            ABSENT_KEY -> ""
            null -> ""","kind":null"""
            else -> ""","kind":"$kind""""
        }
        return """{"sessionId":"$id","courseId":"c-$id","courseName":"Salsa","instructorName":"Ana",""" +
            """"scheduledAt":"$scheduledAt","status":"ATTENDED","recordedAt":$recorded$kindField}"""
    }

    private companion object {
        const val ABSENT_KEY = "<absent>"
    }
}
