package com.menta.android.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menta.android.di.IoDispatcher
import com.menta.android.domain.model.StudentSessionResult
import com.menta.android.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val signedInChannel = Channel<Unit>(Channel.BUFFERED)

    /** Fires once per successful student login. */
    val signedIn: Flow<Unit> = signedInChannel.receiveAsFlow()

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, emailRequired = false) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, passwordRequired = false) }

    fun onSubmit() {
        val current = _uiState.value
        if (current.isLoading) return

        val emailRequired = current.email.isBlank()
        val passwordRequired = current.password.isEmpty()
        if (emailRequired || passwordRequired) {
            _uiState.update { it.copy(emailRequired = emailRequired, passwordRequired = passwordRequired) }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = withContext(io) { loginUseCase(current.email.trim(), current.password) }
            _uiState.update { it.copy(isLoading = false, error = result.toError()) }
            if (result == StudentSessionResult.Student) signedInChannel.send(Unit)
        }
    }

    private fun StudentSessionResult.toError(): LoginError? = when (this) {
        StudentSessionResult.Student -> null
        StudentSessionResult.NotStudent -> LoginError.StudentsOnly
        StudentSessionResult.SignedOut -> LoginError.Unavailable
        is StudentSessionResult.Failed -> failure.toLoginError()
    }
}
