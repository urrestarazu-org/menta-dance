package com.menta.android.domain.model

/** A safe, wire-independent authentication failure. Never carries token values. */
data class AuthFailure(
    val code: String,
    val retryAfterSeconds: Long? = null,
)
