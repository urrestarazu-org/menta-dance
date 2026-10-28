package com.menta.android.presentation.mysessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.menta.android.di.IoDispatcher
import com.menta.android.domain.model.MySessionsResult
import com.menta.android.domain.usecase.GetMySessionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MySessionsViewModel @Inject constructor(
    private val getMySessions: GetMySessionsUseCase,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MySessionsUiState>(MySessionsUiState.Loading)
    val uiState: StateFlow<MySessionsUiState> = _uiState.asStateFlow()

    private var inFlight: Job? = null

    /**
     * Fetches the list. The screen calls it on every resume, so there is no load in `init`.
     * Ignored while a fetch is running; current content stays visible until the result arrives.
     */
    fun refresh() {
        if (inFlight?.isActive == true) return
        if (_uiState.value == MySessionsUiState.Error) _uiState.value = MySessionsUiState.Loading
        inFlight = viewModelScope.launch {
            _uiState.value = withContext(io) { getMySessions() }.toUiState()
        }
    }

    private fun MySessionsResult.toUiState(): MySessionsUiState = when (this) {
        is MySessionsResult.Loaded ->
            if (sessions.isEmpty()) MySessionsUiState.Empty else MySessionsUiState.Loaded(sessions)
        MySessionsResult.Unavailable -> MySessionsUiState.Error
    }
}
