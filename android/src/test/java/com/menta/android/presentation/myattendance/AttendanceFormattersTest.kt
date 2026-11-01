package com.menta.android.presentation.myattendance

import com.menta.android.R
import com.menta.android.domain.model.AttendanceOrigin
import com.menta.android.presentation.mysessions.formatSessionTime
import java.time.Instant
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttendanceFormattersTest {
    private val esAr = Locale.forLanguageTag("es-AR")

    @Test
    fun month_label_is_spanish_and_lowercase() {
        assertEquals("septiembre de 2026", formatMonthLabel(YearMonth.of(2026, 9), esAr))
    }

    @Test
    fun check_in_time_is_rendered_in_the_given_zone() {
        assertEquals("19:02", formatCheckInTime(Instant.parse("2026-09-03T22:02:00Z"), ACADEMY_ZONE))
    }

    @Test
    fun check_in_time_follows_the_academy_zone_across_midnight() {
        assertEquals("23:30", formatCheckInTime(Instant.parse("2026-10-01T02:30:00Z"), ACADEMY_ZONE))
    }

    @Test
    fun a_known_origin_maps_to_its_check_in_string() {
        assertEquals(R.string.my_attendance_check_in_qr, AttendanceOrigin.QR.labelRes())
        assertEquals(R.string.my_attendance_check_in_manual, AttendanceOrigin.MANUAL.labelRes())
    }

    @Test
    fun an_unknown_origin_has_no_label() {
        assertNull(AttendanceOrigin.UNKNOWN.labelRes())
    }

    @Test
    fun class_time_is_cut_in_buenos_aires_time() {
        assertEquals(
            "jueves 3 de septiembre, 19:00",
            formatSessionTime(Instant.parse("2026-09-03T22:00:00Z"), ACADEMY_ZONE, esAr),
        )
        assertEquals(
            "miércoles 30 de septiembre, 23:30",
            formatSessionTime(Instant.parse("2026-10-01T02:30:00Z"), ACADEMY_ZONE, esAr),
        )
    }
}
