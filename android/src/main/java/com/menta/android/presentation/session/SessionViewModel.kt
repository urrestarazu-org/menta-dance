package com.menta.android.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menta.android.di.IoDispatcher
import com.menta.android.domain.model.AuthFailure
import com.menta.android.domain.model.StudentSessionResult
import com.menta.android.domain.usecase.LogoutUseCase
import com.menta.android.domain.usecase.ObserveSessionExpiryUseCase
import com.menta.android.domain.usecase.RefreshSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Activity-scoped owner of the [SessionGate]: cold-start resume, logout and mid-use expiry. */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val refreshSession: RefreshSessionUseCase,
    private val logout: LogoutUseCase,
    observeSessionExpiry: ObserveSessionExpiryUseCase,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _gate = MutableStateFlow<SessionGate>(SessionGate.Splash)
    val gate: StateFlow<SessionGate> = _gate.asStateFlow()

    init {
        viewModelScope.launch {
            _gate.value = withContext(io) { refreshSession() }.toGate()
        }
        viewModelScope.launch {
            observeSessionExpiry().collect { _gate.value = SessionGate.SignedOut(SessionNotice.SessionExpired) }
        }
    }

    fun onSignedIn() {
        _gate.value = SessionGate.SignedIn
    }

    fun onLogout() {
        viewModelScope.launch {
            // The repository wipes locally even when revocation fails, so the user is signed out regardless.
            runCatching { withContext(io) { logout() } }
            _gate.value = SessionGate.SignedOut(null)
        }
    }

    private fun StudentSessionResult.toGate(): SessionGate = when (this) {
        StudentSessionResult.Student -> SessionGate.SignedIn
        StudentSessionResult.SignedOut -> SessionGate.SignedOut(null)
        StudentSessionResult.NotStudent -> SessionGate.SignedOut(SessionNotice.StudentsOnly)
        is StudentSessionResult.Failed -> SessionGate.SignedOut(
            if (failure == AuthFailure.SessionRejected) SessionNotice.ResumeRejected else SessionNotice.ResumeUnavailable,
        )
    }
}
