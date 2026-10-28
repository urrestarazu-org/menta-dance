package com.menta.android.domain.model

/** Why the server refused to issue an access QR; none of them is worth retrying. */
enum class AccessDenial {
    OUTSIDE_CHECK_IN_WINDOW,
    SESSION_CANCELLED,
    CAPACITY_ASSIGNMENT_REQUIRED,
    SESSION_NOT_FOUND,
    OTHER,
}
