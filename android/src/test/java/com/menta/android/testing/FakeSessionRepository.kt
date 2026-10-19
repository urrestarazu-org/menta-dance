package com.menta.android.testing

import com.menta.android.domain.model.SessionState
import com.menta.android.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class FakeSessionRepository : SessionRepository {
    var loginResult: SessionState = SessionState.SignedOut
    var refreshResult: SessionState = SessionState.SignedOut
    var logoutCalls = 0
    var loginCalls = 0

    override var sessionExpired: Flow<Unit> = emptyFlow()

    override fun login(email: String, password: String): SessionState {
        loginCalls++
        return loginResult
    }

    override fun refresh(): SessionState = refreshResult

    override fun logout() {
        logoutCalls++
    }
}
