package com.menta.android.domain.model

sealed interface AttendanceHistoryResult {
    data class Loaded(val entries: List<AttendanceEntry>) : AttendanceHistoryResult

    /** Network, server or parsing failure: nothing the user can act on except retrying. */
    data object Unavailable : AttendanceHistoryResult
}
