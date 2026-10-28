package com.menta.android.domain.model

sealed interface AccessQrResult {
    data class Issued(val credential: AccessCredential) : AccessQrResult

    data class Denied(val reason: AccessDenial) : AccessQrResult

    /** Network, server or parsing failure: retrying later may succeed. */
    data object Unavailable : AccessQrResult
}
