package com.menta.bff.application.usecase;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;
import java.util.stream.Stream;

/** Spanish display labels for the physical classes pages. */
final class ClassLabels {

    private static final DateTimeFormatter MOMENT_FORMAT =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", Locale.forLanguageTag("es-AR"));

    private static final Locale AR = Locale.forLanguageTag("es-AR");

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
     * Joins the weekday and start time of a class, e.g. {@code "Lunes 18:00"}.
     *
     * @param dayOfWeek upstream weekday code, may be {@code null} or unknown
     * @param startTime start time as {@code HH:mm}, may be {@code null} or blank
     * @return the label, or {@code null} when neither part is usable
     */
    static String schedule(String dayOfWeek, String startTime) {
        return Stream.of(dayLabel(dayOfWeek), startTime)
                .filter(part -> part != null && !part.isBlank())
                .reduce((day, time) -> day + " " + time)
                .orElse(null);
    }

    private static String dayLabel(String dayOfWeek) {
        if (dayOfWeek == null) {
            return null;
        }
        try {
            return switch (DayOfWeek.valueOf(dayOfWeek.trim().toUpperCase(Locale.ROOT))) {
                case MONDAY -> "Lunes";
                case TUESDAY -> "Martes";
                case WEDNESDAY -> "Miércoles";
                case THURSDAY -> "Jueves";
                case FRIDAY -> "Viernes";
                case SATURDAY -> "Sábado";
                case SUNDAY -> "Domingo";
            };
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
