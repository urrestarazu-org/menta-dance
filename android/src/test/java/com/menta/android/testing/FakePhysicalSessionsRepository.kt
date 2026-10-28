package com.menta.android.testing

import com.menta.android.domain.model.MySessionsResult
import com.menta.android.domain.repository.PhysicalSessionsRepository

class FakePhysicalSessionsRepository : PhysicalSessionsRepository {
    /** Returned by every call; tests reassign it between calls to script a sequence. */
    var mySessionsResult: MySessionsResult = MySessionsResult.Loaded(emptyList())
    var mySessionsCalls = 0

    override fun mySessions(): MySessionsResult {
        mySessionsCalls++
        return mySessionsResult
    }
}
