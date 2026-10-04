package com.menta.bff.application.usecase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Spanish display labels for the in-person class pages. Upstream codes stay
 * English (they are code); only what the student reads is translated, and an
 * unknown code is shown as-is rather than hidden.
 */
final class ClassLabels {

    private static final Locale SPANISH = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter SESSION_FORMAT =
            DateTimeFormatter.ofPattern("EEEE d/M · HH:mm", SPANISH);
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter.ofPattern("d/M HH:mm", SPANISH);

    private static final Map<String, String> DAYS = Map.of(
            "MONDAY", "Lunes",
            "TUESDAY", "Martes",
            "WEDNESDAY", "Miércoles",
            "THURSDAY", "Jueves",
            "FRIDAY", "Viernes",
            "SATURDAY", "Sábado",
            "SUNDAY", "Domingo");

    private static final Map<String, String> LEVELS = Map.of(
            "BEGINNER", "Inicial",
            "INTERMEDIATE", "Intermedio",
            "ADVANCED", "Avanzado");

    private ClassLabels() {
    }

    static String level(String level) {
        return LEVELS.getOrDefault(level, level);
    }

    /** Weekly schedule, e.g. {@code "Martes 19:00"}. */
    static String schedule(String dayOfWeek, String startTime) {
        return DAYS.getOrDefault(dayOfWeek, dayOfWeek) + " " + startTime;
    }

    /** One session's start in the academy zone, e.g. {@code "Martes 6/10 · 19:00"}. */
    static String sessionStart(Instant scheduledAt, ZoneId zone) {
        String label = SESSION_FORMAT.format(scheduledAt.atZone(zone));
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }

    /** A quote's expiry in the academy zone, e.g. {@code "6/10 20:15"}. */
    static String expiry(Instant expiresAt, ZoneId zone) {
        return EXPIRY_FORMAT.format(expiresAt.atZone(zone));
    }
}
