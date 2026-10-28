package com.menta.android.presentation.mysessions

import com.menta.android.domain.model.MySession

sealed interface MySessionsUiState {
    data object Loading : MySessionsUiState

    data object Empty : MySessionsUiState

    data object Error : MySessionsUiState

    data class Loaded(val sessions: List<MySession>) : MySessionsUiState
}
