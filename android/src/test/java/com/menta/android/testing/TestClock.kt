package com.menta.android.testing

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler

/** A [Clock] driven by the test scheduler, so `delay` and expiry checks share the same virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class TestClock(
    private val scheduler: TestCoroutineScheduler,
    val origin: Instant = Instant.parse("2026-10-05T18:00:00Z"),
) : Clock() {
    override fun instant(): Instant = origin.plusMillis(scheduler.currentTime)

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this
}
