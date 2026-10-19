package com.menta.android.domain.model

/** Outcome of an authentication attempt after the students-only gate has been applied. */
sealed interface StudentSessionResult {
    data object Student : StudentSessionResult

    /** Valid credentials of a non-student account; the session was revoked and wiped before returning. */
    data object NotStudent : StudentSessionResult

    data object SignedOut : StudentSessionResult

    data class Failed(val failure: AuthFailure) : StudentSessionResult
}
