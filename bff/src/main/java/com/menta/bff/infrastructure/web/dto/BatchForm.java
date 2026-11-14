package com.menta.bff.infrastructure.web.dto;

import com.menta.bff.infrastructure.web.validation.IsoDate;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form of the batch creation on {@code /gestion/cursos-presenciales/{courseId}/sesiones/lote} (#368): the first
 * and last day, in the academy's zone, of the range in which one session is created for each day of the
 * course's weekday.
 *
 * <p>The checks it makes are the ones that need no API: both dates present and real, the end not before the
 * start (the API refuses an inverted range too) and a range of at most {@value #MAX_DAYS} days, a cap of this
 * screen only (the API has none) that keeps one submission to a sensible number of sessions. Whether any date
 * falls on the course's weekday, and whether the course already has a session at one of them, are the API's
 * decisions. The checks of the range are named {@code to...} on purpose: the page lists them with
 * {@code #fields.errors('to*')} next to the end date's input.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
public class BatchForm {

    /** Longest range, first and last day included. */
    public static final int MAX_DAYS = 366;

    @NotBlank(message = "Ingresá la fecha de inicio")
    @IsoDate(message = "Ingresá una fecha de inicio válida, con el formato aaaa-mm-dd")
    private String from;

    @NotBlank(message = "Ingresá la fecha de fin")
    @IsoDate(message = "Ingresá una fecha de fin válida, con el formato aaaa-mm-dd")
    private String to;

    /** Only judged when both dates are readable: those have their own message. */
    @AssertTrue(message = "La fecha de fin no puede ser anterior a la de inicio")
    public boolean isToOrdered() {
        LocalDate first = fromDate();
        LocalDate last = toDate();
        return first == null || last == null || !last.isBefore(first);
    }

    /** Only judged when both dates are readable and in order: the order has its own message. */
    @AssertTrue(message = "El rango no puede superar los " + MAX_DAYS + " días")
    public boolean isToWithinLimit() {
        LocalDate first = fromDate();
        LocalDate last = toDate();
        return first == null || last == null || last.isBefore(first)
                || ChronoUnit.DAYS.between(first, last) < MAX_DAYS;
    }

    /**
     * The first day of the range.
     *
     * @return the date, or {@code null} while the text is not a date
     */
    public LocalDate fromDate() {
        return parse(from);
    }

    /**
     * The last day of the range.
     *
     * @return the date, or {@code null} while the text is not a date
     */
    public LocalDate toDate() {
        return parse(to);
    }

    /**
     * Stores the first day without surrounding whitespace.
     *
     * @param from the submitted date, possibly null
     */
    public void setFrom(String from) {
        this.from = from == null ? null : from.strip();
    }

    /**
     * Stores the last day without surrounding whitespace.
     *
     * @param to the submitted date, possibly null
     */
    public void setTo(String to) {
        this.to = to == null ? null : to.strip();
    }

    private static LocalDate parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeException e) {
            return null;
        }
    }
}
