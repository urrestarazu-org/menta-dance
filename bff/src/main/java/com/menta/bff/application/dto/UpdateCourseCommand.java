package com.menta.bff.application.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * What the management form submits to change a course: only the fields that are not {@code null} change. The
 * weekday and time are the ones in the academy's zone and travel together, because the UTC weekday depends on
 * the hour.
 *
 * @param title           new name
 * @param description     new description
 * @param localDay        new weekday in the academy's zone; only with {@code localTime}
 * @param localTime       new start time in the academy's zone; only with {@code localDay}
 * @param durationMinutes new length of each class
 * @param level           new level
 * @param capacity        new default number of spots
 * @param status          {@code ACTIVE} or {@code INACTIVE}
 * @throws IllegalArgumentException if only one of {@code localDay} and {@code localTime} is given
 */
public record UpdateCourseCommand(
        String title, String description, DayOfWeek localDay, LocalTime localTime, Integer durationMinutes,
        String level, Integer capacity, String status) {

    public UpdateCourseCommand {
        if ((localDay == null) != (localTime == null)) {
            throw new IllegalArgumentException("The weekday and the time of a course are changed together");
        }
    }
}
