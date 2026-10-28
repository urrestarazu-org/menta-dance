package com.menta.android.presentation.mysessions

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SESSION_TIME_PATTERN = "EEEE d 'de' MMMM, HH:mm"

/** Renders the session start as a Spanish label in [zone], e.g. "lunes 5 de octubre, 18:30". Pure: zone and locale are inputs. */
fun formatSessionTime(instant: Instant, zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofPattern(SESSION_TIME_PATTERN, locale).withZone(zone).format(instant)
