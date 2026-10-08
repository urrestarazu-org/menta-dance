package com.menta.android.domain.repository

import com.menta.android.domain.model.SessionState
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    /**
     * Emits when an authenticated call proved the session dead (a second 401, or a rejected
     * renewal) and the local session was wiped. Never emitted for a user-initiated logout.
     */
    val sessionExpired: Flow<Unit>

    fun login(email: String, password: String): SessionState

    fun refresh(): SessionState

    fun logout()
}
