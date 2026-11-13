package com.menta.android.domain.time

import java.time.ZoneId

/**
 * The time zone of the user's device. The backend speaks UTC (ADR-0048), so everything that needs
 * a calendar (the month of "Mi asistencia", its UTC bounds) is resolved here, in the user's zone.
 * [current] is read at call time so a zone change on the device is honored without a restart.
 */
fun interface DeviceZone {
    fun current(): ZoneId
}
