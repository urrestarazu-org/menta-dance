package com.menta.bff.application.dto;

/**
 * One {@code PHYSICAL} entry of the public catalog list, flattened from the
 * catalog's {@code physical} block.
 *
 * @param courseId      course identifier
 * @param title         course title
 * @param level         upstream level code ({@code BEGINNER}, {@code INTERMEDIATE}, {@code ADVANCED})
 * @param professorName teacher's display name
 * @param dayOfWeek     upstream weekday code (e.g. {@code TUESDAY})
 * @param startTime     local start time, {@code HH:mm}
 * @param capacity      spots per session
 */
public record PhysicalClassSummary(
        String courseId,
        String title,
        String level,
        String professorName,
        String dayOfWeek,
        String startTime,
        int capacity) {
}
