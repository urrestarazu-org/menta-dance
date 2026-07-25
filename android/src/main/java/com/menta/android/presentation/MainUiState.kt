package com.menta.android.presentation

import androidx.compose.runtime.Immutable

@Immutable
data class MainUiState(
    val message: String = "Loading Android scaffold…",
)
