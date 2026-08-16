package com.menta.android.domain.repository

import com.menta.android.domain.model.SessionState

interface SessionRepository {
    fun login(email: String, password: String): SessionState

    fun refresh(): SessionState

    fun logout()
}
