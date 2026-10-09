package com.menta.android.presentation.login

import com.menta.android.domain.model.AuthFailure

/** What the login screen can tell the user; the screen maps each case to localized copy. */
sealed interface LoginError {
    data object InvalidCredentials : LoginError

    data object AccountLocked : LoginError

    data class RateLimited(val retryAfterSeconds: Long?) : LoginError

    data object Unavailable : LoginError

    /** Valid credentials of a staff account: this app is for students only. */
    data object StudentsOnly : LoginError
}

fun AuthFailure.toLoginError(): LoginError = when (this) {
    AuthFailure.InvalidCredentials, AuthFailure.AccountNotActivated -> LoginError.InvalidCredentials
    AuthFailure.AccountLocked -> LoginError.AccountLocked
    is AuthFailure.RateLimited -> LoginError.RateLimited(retryAfterSeconds)
    AuthFailure.Unavailable,
    AuthFailure.SessionRejected,
    AuthFailure.InvalidResponse,
    AuthFailure.LocalStorageFailed,
    is AuthFailure.Unexpected,
    -> LoginError.Unavailable
}
