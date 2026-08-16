package com.menta.android.data.remote

/**
 * The only data-layer boundary where a raw refresh token exists in memory.
 * Implementations must never log this parameter or response header.
 */
interface AuthRemoteDataSource {
    fun login(email: String, password: String): AuthRemoteResult

    fun refresh(refreshToken: String): AuthRemoteResult

    fun logout(accessToken: String?, refreshToken: String): AuthRemoteResult
}

sealed interface AuthRemoteResult {
    data class Success(
        val accessToken: String?,
        val refreshToken: String?,
        val expiresInSeconds: Long?,
    ) : AuthRemoteResult

    data class Problem(
        val code: String,
        val retryAfterSeconds: Long? = null,
    ) : AuthRemoteResult

    data object Unavailable : AuthRemoteResult
}
