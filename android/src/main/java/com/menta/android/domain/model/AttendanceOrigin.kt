package com.menta.android.domain.model

/** How a check-in was recorded. [UNKNOWN] covers a missing, null or unrecognised value from the server. */
enum class AttendanceOrigin { QR, MANUAL, UNKNOWN }
