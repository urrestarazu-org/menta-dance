package com.menta.bff.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What the management form submits to change a session: only the fields that are not {@code null} change. The
 * date and time are the ones in the academy's zone and travel together, because the UTC date depends on the hour.
 *
 * @param localDate new date in the academy's zone; only with {@code localTime}
 * @param localTime new start time in the academy's zone; only with {@code localDate}
 * @param capacity  new number of spots
 * @param notes     new notes
 * @param status    {@code SCHEDULED} or {@code CANCELLED}
 * @throws IllegalArgumentException if only one of {@code localDate} and {@code localTime} is given
 */
public record UpdateSessionCommand(
        LocalDate localDate, LocalTime localTime, Integer capacity, String notes, String status) {

    public UpdateSessionCommand {
        if ((localDate == null) != (localTime == null)) {
            throw new IllegalArgumentException("The date and the time of a session are changed together");
        }
    }
}
