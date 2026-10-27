package com.menta.android.domain.repository

import com.menta.android.domain.model.MySessionsResult

interface PhysicalSessionsRepository {
    /** Blocking: call off the main thread. */
    fun mySessions(): MySessionsResult
}
