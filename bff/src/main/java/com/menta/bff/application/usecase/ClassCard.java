package com.menta.bff.application.usecase;

/**
 * One row of the class list, already labelled for display.
 *
 * @param courseId      course identifier, for the detail link
 * @param title         course title
 * @param level         Spanish level label
 * @param professorName teacher's display name
 * @param schedule      weekly schedule label, e.g. {@code "Martes 19:00"}
 * @param capacity      spots per session
 */
public record ClassCard(
        String courseId, String title, String level, String professorName, String schedule, int capacity) {
}
