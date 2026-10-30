package com.menta.android.presentation.myattendance

import com.menta.android.domain.model.AttendanceEntry
import java.time.YearMonth

/** The selector (month, next button) is visible in every state, so it varies independently of [content]. */
data class AttendanceHistoryUiState(
    val month: YearMonth,
    val isNextEnabled: Boolean,
    val content: AttendanceContent,
)

sealed interface AttendanceContent {
    data object Loading : AttendanceContent

    data object Empty : AttendanceContent

    data object Error : AttendanceContent

    data class Loaded(val entries: List<AttendanceEntry>) : AttendanceContent
}
