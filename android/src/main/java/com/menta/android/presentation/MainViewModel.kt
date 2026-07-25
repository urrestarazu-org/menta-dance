package com.menta.android.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.menta.android.domain.usecase.GetWelcomeMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getWelcomeMessageUseCase: GetWelcomeMessageUseCase,
) : ViewModel() {

    var uiState by mutableStateOf(MainUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        uiState = MainUiState(
            message = getWelcomeMessageUseCase().text,
        )
    }
}
