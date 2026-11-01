package com.menta.android.presentation.accessqr

import com.menta.android.domain.model.AccessCredential
import com.menta.android.domain.model.AccessDenial

/** The credential lives only here. [Showing.toString] is masked because [AccessCredential.toString] is. */
sealed interface AccessQrUiState {
    data object Loading : AccessQrUiState

    data class Showing(val credential: AccessCredential) : AccessQrUiState

    /** No valid QR and the server is unreachable; retries continue while the screen is visible. */
    data object Offline : AccessQrUiState

    data class Denied(val reason: AccessDenial) : AccessQrUiState
}
