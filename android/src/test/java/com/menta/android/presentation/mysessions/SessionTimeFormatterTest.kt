package com.menta.android.presentation.mysessions

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTimeFormatterTest {
    private val esAr = Locale.forLanguageTag("es-AR")
    private val buenosAires = ZoneId.of("America/Argentina/Buenos_Aires")

    @Test
    fun formats_the_start_in_the_given_zone_with_a_spanish_label() {
        val label = formatSessionTime(Instant.parse("2026-10-05T21:30:00Z"), buenosAires, esAr)

        assertEquals("lunes 5 de octubre, 18:30", label)
    }

    @Test
    fun the_same_instant_moves_to_the_next_day_in_another_zone() {
        val instant = Instant.parse("2026-10-06T01:30:00Z")

        assertEquals("lunes 5 de octubre, 22:30", formatSessionTime(instant, buenosAires, esAr))
        assertEquals("martes 6 de octubre, 01:30", formatSessionTime(instant, ZoneOffset.UTC, esAr))
    }
}
