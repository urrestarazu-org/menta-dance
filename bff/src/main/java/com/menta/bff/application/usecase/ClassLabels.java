package com.menta.bff.application.usecase;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/** Spanish display labels for the physical classes pages. */
final class ClassLabels {

    private static final DateTimeFormatter MOMENT_FORMAT =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", Locale.forLanguageTag("es-AR"));

    private static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-AR"));

    private static final DateTimeFormatter SHORT_MOMENT_FORMAT =
            DateTimeFormatter.ofPattern("EEEE dd/MM HH:mm", Locale.forLanguageTag("es-AR"));

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
     * Words an instant in the academy's zone in its short form, e.g. {@code "martes 17/11 22:00"}.
     *
     * @param instant moment to word
     * @param zone    academy display zone
     * @return the Spanish label
     */
    static String shortMoment(Instant instant, ZoneId zone) {
        return SHORT_MOMENT_FORMAT.format(instant.atZone(zone));
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
            WeeklySlot local = toLocalSlot(utcDay, time, zone);
            dayPart = day == null ? null : dayLabel(local.day());
            timePart = TIME_FORMAT.format(local.time());
        }
        return Stream.of(dayPart, timePart)
                .filter(part -> part != null && !part.isBlank())
                .reduce((d, t) -> d + " " + t)
                .orElse(null);
    }

    /**
     * A weekday and a time of day, the shape of a weekly recurrence. Which zone it is in depends on the method
     * that produced it.
     */
    record WeeklySlot(DayOfWeek day, LocalTime time) {
    }

    /** An inclusive range of dates. */
    record DateRange(LocalDate from, LocalDate to) {
    }

    /**
     * Converts the weekly recurrence of a class from the academy's zone to UTC, the inverse of
     * {@link #toLocalSlot}: Tuesday 22:00 in Buenos Aires is Wednesday 01:00 UTC. The weekday rolls with the hour,
     * forward west of UTC and backwards east of it. Like {@link #schedule}, it uses a fixed reference week, never
     * the current date, so it is deterministic.
     *
     * <p>Limitation (ADR-0048): a recurrence fixed in UTC is exact only for zones without daylight saving time,
     * as Buenos Aires. For a zone with it, the offset of the reference week (January) is used all year round.
     * </p>
     *
     * @param localDay  weekday in the academy's zone
     * @param localTime start time in the academy's zone
     * @param zone      academy display zone
     * @return the weekday and time in UTC
     * @throws NullPointerException if any argument is {@code null}
     */
    static WeeklySlot toUtcSlot(DayOfWeek localDay, LocalTime localTime, ZoneId zone) {
        Objects.requireNonNull(zone, "zone");
        return convertSlot(localDay, localTime, zone, ZoneOffset.UTC);
    }

    /**
     * Converts the weekly recurrence of a class from UTC (how the API stores it) to the academy's zone, e.g. to
     * prefill an edit form. Same reference week and same daylight saving limitation as {@link #toUtcSlot}.
     *
     * @param utcDay  weekday in UTC
     * @param utcTime start time in UTC
     * @param zone    academy display zone
     * @return the weekday and time in the academy's zone
     * @throws NullPointerException if any argument is {@code null}
     */
    static WeeklySlot toLocalSlot(DayOfWeek utcDay, LocalTime utcTime, ZoneId zone) {
        Objects.requireNonNull(zone, "zone");
        return convertSlot(utcDay, utcTime, ZoneOffset.UTC, zone);
    }

    /**
     * Converts a local date and time in the academy's zone to the UTC date and time the API expects for a single
     * session: Tuesday 2026-11-17 22:00 in Buenos Aires is 2026-11-18 01:00 UTC. A local time that does not exist
     * (a daylight saving gap) is moved forward by the zone rules.
     *
     * @param localDate date in the academy's zone
     * @param localTime time in the academy's zone
     * @param zone      academy display zone
     * @return the UTC date and time
     * @throws NullPointerException if any argument is {@code null}
     */
    static LocalDateTime toUtc(LocalDate localDate, LocalTime localTime, ZoneId zone) {
        Objects.requireNonNull(localDate, "localDate");
        Objects.requireNonNull(localTime, "localTime");
        Objects.requireNonNull(zone, "zone");
        return localDate.atTime(localTime).atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * Converts the local range of a batch of sessions to the UTC dates the API takes. The API generates one session
     * per UTC date in the range whose UTC weekday is the course's, at the course's UTC time, so each end of the
     * range is moved together with the course's local start time: for a course that is Tuesday 22:00 in Buenos
     * Aires, the local range 2026-11-01..2026-11-30 becomes 2026-11-02..2026-12-01, and the four Wednesdays 01:00
     * UTC inside it are the four local Tuesdays 22:00 the user expects. An inverted range stays inverted, and the
     * API refuses it.
     *
     * @param localFrom       first local date, inclusive
     * @param localTo         last local date, inclusive
     * @param courseLocalTime start time of the course in the academy's zone
     * @param zone            academy display zone
     * @return the UTC dates, inclusive
     * @throws NullPointerException if any argument is {@code null}
     */
    static DateRange toUtcDateRange(LocalDate localFrom, LocalDate localTo, LocalTime courseLocalTime, ZoneId zone) {
        return new DateRange(
                toUtc(localFrom, courseLocalTime, zone).toLocalDate(),
                toUtc(localTo, courseLocalTime, zone).toLocalDate());
    }

    private static WeeklySlot convertSlot(DayOfWeek day, LocalTime time, ZoneId from, ZoneId to) {
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(time, "time");
        ZonedDateTime moved = REFERENCE_MONDAY.with(TemporalAdjusters.nextOrSame(day))
                .atTime(time).atZone(from).withZoneSameInstant(to);
        return new WeeklySlot(moved.getDayOfWeek(), moved.toLocalTime());
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
