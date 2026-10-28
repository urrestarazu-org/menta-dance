package com.menta.android.presentation.accessqr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menta.android.di.IoDispatcher
import com.menta.android.domain.model.AccessCredential
import com.menta.android.domain.model.AccessQrResult
import com.menta.android.domain.usecase.IssueAccessQrUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the access QR while its screen is visible. The screen calls [onVisible] / [onHidden]
 * from its lifecycle; the credential is dropped on hide and is never saved anywhere else.
 */
@HiltViewModel(assistedFactory = AccessQrViewModel.Factory::class)
class AccessQrViewModel @AssistedInject constructor(
    @Assisted private val sessionId: String,
    private val issueAccessQr: IssueAccessQrUseCase,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(sessionId: String): AccessQrViewModel
    }

    private val _uiState = MutableStateFlow<AccessQrUiState>(AccessQrUiState.Loading)
    val uiState: StateFlow<AccessQrUiState> = _uiState.asStateFlow()

    private var renewal: Job? = null

    fun onVisible() {
        if (renewal?.isActive == true) return
        renewal = viewModelScope.launch { renewalLoop() }
    }

    fun onHidden() {
        renewal?.cancel()
        renewal = null
        _uiState.value = AccessQrUiState.Loading
    }

    /**
     * Issue, show, wait, repeat. A denial ends the loop; a transient failure keeps the current QR
     * until it expires and retries with exponential backoff, reset by the next success.
     */
    private suspend fun renewalLoop() {
        var failures = 0
        var shown: AccessCredential? = null
        while (true) {
            if (shown?.isExpiredAt(clock.instant()) == true) {
                shown = null
                _uiState.value = AccessQrUiState.Offline
            }
            when (val result = withContext(io) { issueAccessQr(sessionId) }) {
                is AccessQrResult.Issued -> {
                    failures = 0
                    shown = result.credential
                    _uiState.value = AccessQrUiState.Showing(result.credential)
                    delay(result.credential.renewalDelay(clock.instant()).toMillis())
                }
                is AccessQrResult.Denied -> {
                    _uiState.value = AccessQrUiState.Denied(result.reason)
                    return
                }
                AccessQrResult.Unavailable -> {
                    failures++
                    val backoff = backoff(failures)
                    val stillValid = shown?.takeIf { !it.isExpiredAt(clock.instant()) }
                    if (stillValid == null) {
                        shown = null
                        _uiState.value = AccessQrUiState.Offline
                        delay(backoff.toMillis())
                    } else {
                        // Wake up at expiry at the latest so the stale QR is cleared on time.
                        val untilExpiry = Duration.between(clock.instant(), stillValid.expiresAt)
                        delay(minOf(backoff, untilExpiry).toMillis())
                    }
                }
            }
        }
    }

    /** 2 s, 4 s, 8 s, ... capped at [MAX_BACKOFF]. */
    private fun backoff(failures: Int): Duration {
        val exponent = (failures - 1).coerceIn(0, MAX_BACKOFF_EXPONENT)
        return minOf(INITIAL_BACKOFF.multipliedBy(1L shl exponent), MAX_BACKOFF)
    }

    private companion object {
        val INITIAL_BACKOFF: Duration = Duration.ofSeconds(2)
        val MAX_BACKOFF: Duration = Duration.ofSeconds(30)
        const val MAX_BACKOFF_EXPONENT = 5
    }
}
