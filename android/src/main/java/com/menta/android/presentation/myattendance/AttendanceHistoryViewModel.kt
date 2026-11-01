package com.menta.android.presentation.myattendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menta.android.di.IoDispatcher
import com.menta.android.domain.model.AttendanceHistoryResult
import com.menta.android.domain.usecase.GetMyAttendanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Fixed business zone of the academy: the month is cut here, never in UTC or the device zone. */
internal val ACADEMY_ZONE: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")

@HiltViewModel
class AttendanceHistoryViewModel @Inject constructor(
    private val getMyAttendance: GetMyAttendanceUseCase,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AttendanceHistoryUiState(month = current(), isNextEnabled = false, content = AttendanceContent.Loading),
    )
    val uiState: StateFlow<AttendanceHistoryUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    /**
     * Fetches the selected month. The screen calls it on every resume, so there is no load in `init`.
     * Ignored while a load is running; current content stays visible until the result arrives.
     */
    fun refresh() {
        if (job?.isActive == true) return
        // The month may have rolled over while the screen was away, so next is re-evaluated here.
        _uiState.update {
            it.copy(
                isNextEnabled = isBeforeCurrent(it.month),
                content = if (it.content == AttendanceContent.Error) AttendanceContent.Loading else it.content,
            )
        }
        load(_uiState.value.month)
    }

    fun showPreviousMonth() = select(_uiState.value.month.minusMonths(1))

    /** No-op at the current month: the future cannot be selected. */
    fun showNextMonth() {
        val month = _uiState.value.month
        if (isBeforeCurrent(month)) select(month.plusMonths(1))
    }

    private fun select(month: YearMonth) {
        job?.cancel()
        _uiState.value = AttendanceHistoryUiState(month, isNextEnabled = isBeforeCurrent(month), content = AttendanceContent.Loading)
        load(month)
    }

    private fun load(month: YearMonth) {
        job = viewModelScope.launch {
            val result = withContext(io) { getMyAttendance(month) }
            // The blocking call cannot be aborted; a result for a superseded month is discarded.
            _uiState.update { if (it.month == month) it.copy(content = result.toContent()) else it }
        }
    }

    private fun AttendanceHistoryResult.toContent(): AttendanceContent = when (this) {
        is AttendanceHistoryResult.Loaded ->
            if (entries.isEmpty()) AttendanceContent.Empty else AttendanceContent.Loaded(entries)
        AttendanceHistoryResult.Unavailable -> AttendanceContent.Error
    }

    private fun isBeforeCurrent(month: YearMonth) = month < current()

    /** `TestClock.withZone` returns itself, so the zone is applied to the instant, not to the clock. */
    private fun current(): YearMonth = YearMonth.from(clock.instant().atZone(ACADEMY_ZONE))
}
