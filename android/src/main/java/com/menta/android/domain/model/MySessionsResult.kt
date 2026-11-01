package com.menta.android.domain.model

sealed interface MySessionsResult {
    data class Loaded(val sessions: List<MySession>) : MySessionsResult

    /** Network, server or parsing failure: nothing the user can act on except retrying. */
    data object Unavailable : MySessionsResult
}
