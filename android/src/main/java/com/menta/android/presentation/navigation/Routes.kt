package com.menta.android.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
data object Splash

@Serializable
data object Login

/** Nested graph that holds every screen reachable only with a student session. */
@Serializable
data object Authenticated

@Serializable
data object Home

@Serializable
data object MySessions

/** Only the non-secret session id travels as an argument; the credential never does. */
@Serializable
data class AccessQr(val sessionId: String)
