package com.menta.android.presentation.session

/** Where the app currently stands: resolving the stored session, signed out, or inside the student area. */
sealed interface SessionGate {
    data object Splash : SessionGate

    data class SignedOut(val notice: SessionNotice?) : SessionGate

    data object SignedIn : SessionGate
}

/** Why the user landed on Login without having asked for it. */
enum class SessionNotice { SessionExpired, ResumeRejected, ResumeUnavailable, StudentsOnly }
