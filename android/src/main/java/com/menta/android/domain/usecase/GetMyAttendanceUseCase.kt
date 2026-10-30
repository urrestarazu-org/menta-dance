package com.menta.android.domain.usecase

import com.menta.android.domain.model.AttendanceHistoryResult
import com.menta.android.domain.repository.AttendanceHistoryRepository
import java.time.YearMonth
import javax.inject.Inject

class GetMyAttendanceUseCase @Inject constructor(
    private val repository: AttendanceHistoryRepository,
) {
    /** Blocking: call off the main thread. */
    operator fun invoke(month: YearMonth): AttendanceHistoryResult = repository.monthlyAttendance(month)
}
