package com.menta.android.testing

import com.menta.android.domain.model.AttendanceHistoryResult
import com.menta.android.domain.repository.AttendanceHistoryRepository
import java.time.YearMonth

/**
 * Scripted [AttendanceHistoryRepository]. The call is blocking, so [onRequest] runs inside it and
 * lets a test change the ViewModel's month while a "request" is still in flight (stale results).
 */
class FakeAttendanceHistoryRepository : AttendanceHistoryRepository {
    /** Returned for any month without an entry in [results]. */
    var defaultResult: AttendanceHistoryResult = AttendanceHistoryResult.Loaded(emptyList())
    val results = mutableMapOf<YearMonth, AttendanceHistoryResult>()

    /** Months in the order they were requested. */
    val requestedMonths = mutableListOf<YearMonth>()
    val calls get() = requestedMonths.size

    /** Invoked with the requested month before the result is returned. */
    var onRequest: (YearMonth) -> Unit = {}

    override fun monthlyAttendance(month: YearMonth): AttendanceHistoryResult {
        requestedMonths += month
        onRequest(month)
        return results[month] ?: defaultResult
    }
}
