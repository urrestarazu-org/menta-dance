package com.menta.android.presentation.mysessions

import com.menta.android.domain.model.MySession
import com.menta.android.domain.model.MySessionsResult
import com.menta.android.domain.usecase.GetMySessionsUseCase
import com.menta.android.testing.FakePhysicalSessionsRepository
import com.menta.android.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MySessionsViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val repository = FakePhysicalSessionsRepository()
    private val viewModel = MySessionsViewModel(GetMySessionsUseCase(repository), mainRule.dispatcher)

    private fun session(id: String, checkInOpen: Boolean = false) =
        MySession(id, "Salsa", "Ana", Instant.parse("2026-10-05T21:30:00Z"), checkInOpen)

    @Test
    fun the_view_model_loads_nothing_until_refresh_is_called() = runTest {
        advanceUntilIdle()

        assertEquals(MySessionsUiState.Loading, viewModel.uiState.value)
        assertEquals(0, repository.mySessionsCalls)
    }

    @Test
    fun refresh_shows_loading_and_then_the_loaded_sessions_in_order() = runTest {
        repository.mySessionsResult = MySessionsResult.Loaded(listOf(session("s-1"), session("s-2")))

        viewModel.refresh()
        assertEquals(MySessionsUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()

        assertEquals(MySessionsUiState.Loaded(listOf(session("s-1"), session("s-2"))), viewModel.uiState.value)
    }

    @Test
    fun an_empty_list_produces_the_empty_state() = runTest {
        repository.mySessionsResult = MySessionsResult.Loaded(emptyList())

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(MySessionsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun a_failure_produces_the_error_state_and_retry_issues_a_new_request() = runTest {
        repository.mySessionsResult = MySessionsResult.Unavailable
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(MySessionsUiState.Error, viewModel.uiState.value)

        repository.mySessionsResult = MySessionsResult.Loaded(listOf(session("s-1")))
        viewModel.refresh()
        assertEquals(MySessionsUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()

        assertEquals(2, repository.mySessionsCalls)
        assertEquals(MySessionsUiState.Loaded(listOf(session("s-1"))), viewModel.uiState.value)
    }

    @Test
    fun every_refresh_refetches_and_reflects_a_check_in_window_that_opened() = runTest {
        repository.mySessionsResult = MySessionsResult.Loaded(listOf(session("s-1", checkInOpen = false)))
        viewModel.refresh()
        advanceUntilIdle()
        repository.mySessionsResult = MySessionsResult.Loaded(listOf(session("s-1", checkInOpen = true)))

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, repository.mySessionsCalls)
        assertEquals(MySessionsUiState.Loaded(listOf(session("s-1", checkInOpen = true))), viewModel.uiState.value)
    }

    @Test
    fun a_later_refresh_keeps_the_current_content_until_the_result_arrives() = runTest {
        repository.mySessionsResult = MySessionsResult.Loaded(listOf(session("s-1")))
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.refresh()

        assertEquals(MySessionsUiState.Loaded(listOf(session("s-1"))), viewModel.uiState.value)
    }

    @Test
    fun a_refresh_issued_while_one_is_in_flight_is_ignored() = runTest {
        repository.mySessionsResult = MySessionsResult.Loaded(listOf(session("s-1")))

        viewModel.refresh()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1, repository.mySessionsCalls)
    }

    @Test
    fun check_in_open_is_exposed_per_item() = runTest {
        repository.mySessionsResult =
            MySessionsResult.Loaded(listOf(session("open", checkInOpen = true), session("closed", checkInOpen = false)))

        viewModel.refresh()
        advanceUntilIdle()

        val loaded = viewModel.uiState.value as MySessionsUiState.Loaded
        assertEquals(mapOf("open" to true, "closed" to false), loaded.sessions.associate { it.sessionId to it.checkInOpen })
    }
}
