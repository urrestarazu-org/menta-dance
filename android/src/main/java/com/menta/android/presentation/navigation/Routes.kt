package com.menta.android.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
data object Login

/** Nested graph that holds every screen reachable only with a student session. */
@Serializable
data object Authenticated

@Serializable
data object Home
