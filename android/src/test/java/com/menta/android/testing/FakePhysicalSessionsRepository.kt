package com.menta.android.testing

import com.menta.android.domain.model.AccessQrResult
import com.menta.android.domain.model.MySessionsResult
import com.menta.android.domain.repository.PhysicalSessionsRepository

class FakePhysicalSessionsRepository : PhysicalSessionsRepository {
    /** Returned by every call; tests reassign it between calls to script a sequence. */
    var mySessionsResult: MySessionsResult = MySessionsResult.Loaded(emptyList())
    var mySessionsCalls = 0

    /** Consumed first-in first-out by [issueAccessQr]; once empty, [accessQrResult] is returned. */
    val accessQrScript = ArrayDeque<AccessQrResult>()
    var accessQrResult: AccessQrResult = AccessQrResult.Unavailable
    val accessQrRequests = mutableListOf<String>()
    val accessQrCalls get() = accessQrRequests.size

    override fun mySessions(): MySessionsResult {
        mySessionsCalls++
        return mySessionsResult
    }

    override fun issueAccessQr(sessionId: String): AccessQrResult {
        accessQrRequests += sessionId
        return accessQrScript.removeFirstOrNull() ?: accessQrResult
    }
}
