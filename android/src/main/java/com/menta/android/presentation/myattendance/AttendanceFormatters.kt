package com.menta.android.presentation.myattendance

import androidx.annotation.StringRes
import com.menta.android.R
import com.menta.android.domain.model.AttendanceOrigin
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MONTH_LABEL_PATTERN = "MMMM 'de' yyyy"
private const val CHECK_IN_TIME_PATTERN = "HH:mm"

/** Renders [month] as a Spanish label, e.g. "septiembre de 2026". Pure: the locale is an input. */
fun formatMonthLabel(month: YearMonth, locale: Locale): String =
    month.format(DateTimeFormatter.ofPattern(MONTH_LABEL_PATTERN, locale))

/** Renders the check-in instant as `HH:mm` in [zone]. Pure: the zone is an input. */
fun formatCheckInTime(instant: Instant, zone: ZoneId): String =
    DateTimeFormatter.ofPattern(CHECK_IN_TIME_PATTERN).withZone(zone).format(instant)

/** The check-in string for this origin, or `null` when it is unknown so the line shows the time only. */
@StringRes
fun AttendanceOrigin.labelRes(): Int? = when (this) {
    AttendanceOrigin.QR -> R.string.my_attendance_check_in_qr
    AttendanceOrigin.MANUAL -> R.string.my_attendance_check_in_manual
    AttendanceOrigin.UNKNOWN -> null
}
