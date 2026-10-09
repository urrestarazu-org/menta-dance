package com.menta.android.presentation.login

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val emailRequired: Boolean = false,
    val passwordRequired: Boolean = false,
    val error: LoginError? = null,
) {
    // The password must never reach logs through an accidental toString().
    override fun toString(): String = "LoginUiState(isLoading=$isLoading, error=$error)"
}
