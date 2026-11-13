package com.menta.bff.application.usecase;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Currency;
import java.util.Locale;
import java.util.stream.Stream;

/** Spanish display labels for the physical classes pages. */
final class ClassLabels {

    private static final DateTimeFormatter MOMENT_FORMAT =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", Locale.forLanguageTag("es-AR"));

    private static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-AR"));

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private static final Locale AR = Locale.forLanguageTag("es-AR");

    /** A Monday, fixed so that the weekly schedule conversion never depends on today's date. */
    private static final LocalDate REFERENCE_MONDAY = LocalDate.of(2024, 1, 1);

    private ClassLabels() {
    }

    /**
     * Formats an amount for es-AR with its currency, e.g. {@code "$ 25.000,00"}.
     *
     * @param amount   amount to format
     * @param currency ISO 4217 currency code
     * @return the Spanish label
     */
    static String amount(BigDecimal amount, String currency) {
        NumberFormat format = NumberFormat.getCurrencyInstance(AR);
        format.setCurrency(Currency.getInstance(currency));
        return format.format(amount);
    }

    /**
     * Words an instant in the academy's zone, e.g. {@code "lunes 12 de octubre a las 18:00"}.
     *
     * @param instant moment to word
     * @param zone    academy display zone
     * @return the Spanish label
     */
    static String moment(Instant instant, ZoneId zone) {
        return MOMENT_FORMAT.format(instant.atZone(zone));
    }

    /**
     * Words the day of an instant in the academy's zone, e.g. {@code "lunes 12 de octubre"}.
     *
     * @param instant moment whose day to word
     * @param zone    academy display zone
     * @return the Spanish label
     */
    static String day(Instant instant, ZoneId zone) {
        return DAY_FORMAT.format(instant.atZone(zone));
    }

    /**
     * Words the time of an instant in the academy's zone, e.g. {@code "18:00"}.
     *
     * @param instant moment whose time to word
     * @param zone    academy display zone
     * @return the {@code HH:mm} label
     */
    static String time(Instant instant, ZoneId zone) {
        return TIME_FORMAT.format(instant.atZone(zone));
    }

    /**
     * Joins the weekday and start time of a class, e.g. {@code "Lunes 18:00"}. The upstream recurrence is in UTC
     * (ADR-0048): both parts are converted to the academy's zone, so the weekday rolls with the hour (a class on
     * Wednesday 01:00 UTC is "Martes 22:00" in Buenos Aires). The conversion uses a fixed reference week, never
     * the current date, so the label is deterministic; a zone with daylight saving time is taken at that week's
     * offset.
     *
     * @param dayOfWeek upstream weekday code (UTC), may be {@code null} or unknown
     * @param startTime upstream start time as {@code HH:mm} (UTC), may be {@code null} or blank; one that cannot be
     *                  read is kept as it came, unconverted
     * @param zone      academy display zone
     * @return the label, or {@code null} when neither part is usable
     */
    static String schedule(String dayOfWeek, String startTime, ZoneId zone) {
        DayOfWeek day = parseDay(dayOfWeek);
        LocalTime time = parseTime(startTime);
        String dayPart;
        String timePart;
        if (time == null) {
            dayPart = day == null ? null : dayLabel(day);
            timePart = startTime;
        } else {
            DayOfWeek utcDay = day == null ? DayOfWeek.MONDAY : day;
            ZonedDateTime local = REFERENCE_MONDAY.with(TemporalAdjusters.nextOrSame(utcDay))
                    .atTime(time).atZone(ZoneOffset.UTC).withZoneSameInstant(zone);
            dayPart = day == null ? null : dayLabel(local.getDayOfWeek());
            timePart = TIME_FORMAT.format(local);
        }
        return Stream.of(dayPart, timePart)
                .filter(part -> part != null && !part.isBlank())
                .reduce((d, t) -> d + " " + t)
                .orElse(null);
    }

    private static DayOfWeek parseDay(String dayOfWeek) {
        if (dayOfWeek == null) {
            return null;
        }
        try {
            return DayOfWeek.valueOf(dayOfWeek.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LocalTime parseTime(String startTime) {
        if (startTime == null || startTime.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(startTime.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String dayLabel(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Lunes";
            case TUESDAY -> "Martes";
            case WEDNESDAY -> "Miércoles";
            case THURSDAY -> "Jueves";
            case FRIDAY -> "Viernes";
            case SATURDAY -> "Sábado";
            case SUNDAY -> "Domingo";
        };
    }
}
