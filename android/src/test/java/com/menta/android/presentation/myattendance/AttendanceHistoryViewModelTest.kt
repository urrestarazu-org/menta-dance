package com.menta.android.presentation.myattendance

import com.menta.android.domain.model.AttendanceEntry
import com.menta.android.domain.model.AttendanceHistoryResult
import com.menta.android.domain.model.AttendanceOrigin
import com.menta.android.domain.usecase.GetMyAttendanceUseCase
import com.menta.android.testing.FakeAttendanceHistoryRepository
import com.menta.android.testing.MainDispatcherRule
import com.menta.android.testing.TestClock
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceHistoryViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val repository = FakeAttendanceHistoryRepository()

    private fun viewModelAt(instant: String) = AttendanceHistoryViewModel(
        GetMyAttendanceUseCase(repository),
        Clock.fixed(Instant.parse(instant), ZoneOffset.UTC),
        mainRule.dispatcher,
    )

    // --- Default month and no load in init ---

    @Test
    fun the_default_month_is_the_current_month_in_buenos_aires() {
        val viewModel = viewModelAt("2026-09-15T15:00:00Z")

        assertEquals(YearMonth.of(2026, 9), viewModel.uiState.value.month)
    }

    @Test
    fun the_last_evening_of_a_month_in_buenos_aires_still_belongs_to_that_month() {
        // 2026-10-01T01:30Z is 2026-09-30 22:30 in Buenos Aires.
        assertEquals(YearMonth.of(2026, 9), viewModelAt("2026-10-01T01:30:00Z").uiState.value.month)
        assertEquals(YearMonth.of(2026, 9), viewModelAt(OffsetDateTime.parse("2026-09-30T23:30:00-03:00").toInstant().toString()).uiState.value.month)
    }

    @Test
    fun buenos_aires_midnight_starts_the_next_month() {
        // 2026-10-01T03:00Z is exactly 2026-10-01 00:00 in Buenos Aires.
        assertEquals(YearMonth.of(2026, 10), viewModelAt("2026-10-01T03:00:00Z").uiState.value.month)
    }

    @Test
    fun the_initial_state_is_loading_with_next_disabled_and_no_request_is_issued() = runTest {
        val viewModel = viewModelAt("2026-09-15T15:00:00Z")
        advanceUntilIdle()

        assertEquals(AttendanceContent.Loading, viewModel.uiState.value.content)
        assertFalse(viewModel.uiState.value.isNextEnabled)
        assertEquals(0, repository.calls)
    }

    // --- Load states ---

    private val september = YearMonth.of(2026, 9)

    private fun entry(id: String, origin: AttendanceOrigin = AttendanceOrigin.QR) = AttendanceEntry(
        sessionId = id,
        courseName = "Salsa",
        instructorName = "Ana",
        scheduledAt = Instant.parse("2026-09-03T22:00:00Z"),
        recordedAt = Instant.parse("2026-09-03T22:02:00Z"),
        origin = origin,
    )

    private fun loaded(vararg entries: AttendanceEntry) = AttendanceHistoryResult.Loaded(entries.toList())

    @Test
    fun refresh_shows_loading_and_then_the_loaded_entries_in_order() = runTest {
        val viewModel = viewModelAt("2026-09-15T15:00:00Z")
        repository.results[september] = loaded(entry("a"), entry("b", AttendanceOrigin.MANUAL))

        viewModel.refresh()
        assertEquals(AttendanceContent.Loading, viewModel.uiState.value.content)
        advanceUntilIdle()

        assertEquals(
            AttendanceContent.Loaded(listOf(entry("a"), entry("b", AttendanceOrigin.MANUAL))),
            viewModel.uiState.value.content,
        )
        assertEquals(listOf(september), repository.requestedMonths)
    }

    @Test
    fun an_empty_list_produces_the_empty_state() = runTest {
        val viewModel = viewModelAt("2026-09-15T15:00:00Z")
        repository.results[september] = loaded()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(AttendanceContent.Empty, viewModel.uiState.value.content)
        assertEquals(1, repository.calls)
    }

    @Test
    fun a_failure_produces_the_error_state_and_retry_requests_the_same_month_again() = runTest {
        val viewModel = viewModelAt("2026-09-15T15:00:00Z")
        repository.results[september] = AttendanceHistoryResult.Unavailable
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(AttendanceContent.Error, viewModel.uiState.value.content)

        repository.results[september] = loaded(entry("a"))
        viewModel.refresh()
        assertEquals(AttendanceContent.Loading, viewModel.uiState.value.content)
        advanceUntilIdle()

        assertEquals(listOf(september, september), repository.requestedMonths)
        assertEquals(AttendanceContent.Loaded(listOf(entry("a"))), viewModel.uiState.value.content)
    }

    @Test
    fun a_refresh_for_the_same_month_while_one_is_in_flight_is_ignored() = runTest {
        val viewModel = viewModelAt("2026-09-15T15:00:00Z")
        repository.results[september] = loaded(entry("a"))

        viewModel.refresh()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1, repository.calls)
        assertEquals(AttendanceContent.Loaded(listOf(entry("a"))), viewModel.uiState.value.content)
    }

    @Test
    fun a_resume_refresh_on_a_loaded_state_requests_the_same_month_again_and_keeps_it() = runTest {
        val viewModel = viewModelAt("2026-09-15T15:00:00Z")
        repository.results[september] = loaded(entry("a"))
        viewModel.refresh()
        advanceUntilIdle()

        repository.results[september] = loaded(entry("a"), entry("b"))
        viewModel.refresh()
        assertEquals(AttendanceContent.Loaded(listOf(entry("a"))), viewModel.uiState.value.content)
        advanceUntilIdle()

        assertEquals(listOf(september, september), repository.requestedMonths)
        assertEquals(september, viewModel.uiState.value.month)
        assertEquals(AttendanceContent.Loaded(listOf(entry("a"), entry("b"))), viewModel.uiState.value.content)
    }

    // --- Month navigation ---

    @Test
    fun next_is_a_no_op_at_the_current_month_and_issues_no_request() = runTest {
        val viewModel = viewModelAt("2026-09-15T15:00:00Z")

        viewModel.showNextMonth()
        advanceUntilIdle()

        assertEquals(september, viewModel.uiState.value.month)
        assertFalse(viewModel.uiState.value.isNextEnabled)
        assertEquals(AttendanceContent.Loading, viewModel.uiState.value.content)
        assertEquals(0, repository.calls)
    }

    @Test
    fun previous_selects_the_month_before_shows_loading_enables_next_and_requests_that_month() = runTest {
        val viewModel = viewModelAt("2026-09-15T15:00:00Z")
        repository.results[YearMonth.of(2026, 8)] = loaded(entry("aug"))

        viewModel.showPreviousMonth()

        assertEquals(YearMonth.of(2026, 8), viewModel.uiState.value.month)
        assertTrue(viewModel.uiState.value.isNextEnabled)
        assertEquals(AttendanceContent.Loading, viewModel.uiState.value.content)
        advanceUntilIdle()

        assertEquals(listOf(YearMonth.of(2026, 8)), repository.requestedMonths)
        assertEquals(AttendanceContent.Loaded(listOf(entry("aug"))), viewModel.uiState.value.content)
    }

    @Test
    fun previous_has_no_lower_bound_and_next_stays_enabled_after_24_selections() = runTest {
        val viewModel = viewModelAt("2026-09-15T15:00:00Z")

        repeat(24) { viewModel.showPreviousMonth() }

        assertEquals(YearMonth.of(2024, 9), viewModel.uiState.value.month)
        assertTrue(viewModel.uiState.value.isNextEnabled)
    }

    @Test
    fun next_from_a_past_month_selects_the_following_month_and_is_disabled_again_at_the_current_one() = runTest {
        val viewModel = viewModelAt("2026-09-15T15:00:00Z")
        viewModel.showPreviousMonth()
        viewModel.showPreviousMonth()
        assertEquals(YearMonth.of(2026, 7), viewModel.uiState.value.month)

        viewModel.showNextMonth()
        assertEquals(YearMonth.of(2026, 8), viewModel.uiState.value.month)
        assertTrue(viewModel.uiState.value.isNextEnabled)
        assertEquals(AttendanceContent.Loading, viewModel.uiState.value.content)

        viewModel.showNextMonth()
        assertEquals(september, viewModel.uiState.value.month)
        assertFalse(viewModel.uiState.value.isNextEnabled)
    }

    // --- Last request wins ---

    private val august = YearMonth.of(2026, 8)

    /**
     * While the (blocking) September request is running, the user selects August and that load
     * finishes before September's result returns, which is the only order in which staleness matters.
     */
    private fun selectAugustWhileSeptemberIsInFlight(viewModel: AttendanceHistoryViewModel) {
        repository.onRequest = { month ->
            if (month == september) {
                viewModel.showPreviousMonth()
                mainRule.dispatcher.scheduler.runCurrent()
            }
        }
    }

    @Test
    fun a_stale_success_for_the_superseded_month_is_dropped() = runTest {
        val viewModel = viewModelAt("2026-09-15T15:00:00Z")
        repository.results[september] = loaded(entry("sep"))
        repository.results[august] = loaded(entry("aug"))
        selectAugustWhileSeptemberIsInFlight(viewModel)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf(september, august), repository.requestedMonths)
        assertEquals(august, viewModel.uiState.value.month)
        assertEquals(AttendanceContent.Loaded(listOf(entry("aug"))), viewModel.uiState.value.content)
    }

    @Test
    fun a_stale_failure_for_the_superseded_month_does_not_replace_the_loaded_state() = runTest {
        val viewModel = viewModelAt("2026-09-15T15:00:00Z")
        repository.results[september] = AttendanceHistoryResult.Unavailable
        repository.results[august] = loaded(entry("aug"))
        selectAugustWhileSeptemberIsInFlight(viewModel)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(august, viewModel.uiState.value.month)
        assertEquals(AttendanceContent.Loaded(listOf(entry("aug"))), viewModel.uiState.value.content)
    }

    @Test
    fun a_month_change_before_the_request_starts_requests_only_the_last_month() = runTest {
        val viewModel = viewModelAt("2026-09-15T15:00:00Z")
        repository.results[august] = loaded(entry("aug"))

        viewModel.refresh()
        viewModel.showPreviousMonth()
        advanceUntilIdle()

        assertEquals(listOf(august), repository.requestedMonths)
        assertEquals(AttendanceContent.Loaded(listOf(entry("aug"))), viewModel.uiState.value.content)
    }

    // --- Month rollover while the screen stays alive ---

    private val octoberFirst = YearMonth.of(2026, 10)

    /** Starts 2026-09-30 23:30 in Buenos Aires; one virtual hour later it is 2026-10-01 00:30 there. */
    private fun viewModelBeforeTheSeptemberRollover() = AttendanceHistoryViewModel(
        GetMyAttendanceUseCase(repository),
        TestClock(mainRule.dispatcher.scheduler, Instant.parse("2026-10-01T02:30:00Z")),
        mainRule.dispatcher,
    )

    @Test
    fun a_refresh_after_the_month_rolls_over_enables_next_and_keeps_the_selected_month() = runTest {
        val viewModel = viewModelBeforeTheSeptemberRollover()
        assertEquals(september, viewModel.uiState.value.month)
        assertFalse(viewModel.uiState.value.isNextEnabled)

        advanceTimeBy(60 * 60 * 1000L)
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(september, viewModel.uiState.value.month)
        assertTrue(viewModel.uiState.value.isNextEnabled)
        assertEquals(listOf(september), repository.requestedMonths)
    }

    @Test
    fun next_follows_the_new_current_month_after_the_rollover() = runTest {
        val viewModel = viewModelBeforeTheSeptemberRollover()
        advanceTimeBy(60 * 60 * 1000L)
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.showNextMonth()

        assertEquals(octoberFirst, viewModel.uiState.value.month)
        assertFalse(viewModel.uiState.value.isNextEnabled)
    }

    @Test
    fun a_refresh_without_a_rollover_leaves_next_disabled_and_a_past_month_keeps_it_enabled() = runTest {
        val viewModel = viewModelBeforeTheSeptemberRollover()
        viewModel.refresh()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isNextEnabled)

        viewModel.showPreviousMonth()
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(august, viewModel.uiState.value.month)
        assertTrue(viewModel.uiState.value.isNextEnabled)
    }
}
