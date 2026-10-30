package com.menta.android.domain.repository

import com.menta.android.domain.model.AttendanceHistoryResult
import java.time.YearMonth

interface AttendanceHistoryRepository {
    /** Blocking: call off the main thread. Attended classes of [month], in the server order. */
    fun monthlyAttendance(month: YearMonth): AttendanceHistoryResult
}
