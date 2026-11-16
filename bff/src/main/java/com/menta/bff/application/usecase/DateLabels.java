package com.menta.bff.application.usecase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** The one {@code dd/MM/yyyy} label the student-facing subscription pages show, in the academy's zone. */
final class DateLabels {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DateLabels() {
    }

    static String of(Instant instant, ZoneId zone) {
        return DATE.format(instant.atZone(zone));
    }
}
