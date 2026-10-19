package com.menta.bff.application.usecase;

/**
 * Display model for one physical class in the public list.
 *
 * @param courseId      course identifier
 * @param title         course title
 * @param levelLabel    Spanish level label, {@code null} when unknown
 * @param professorName professor name, {@code null} when not provided
 * @param scheduleLabel weekday and start time, {@code null} when not provided
 */
public record ClassCard(
        String courseId,
        String title,
        String levelLabel,
        String professorName,
        String scheduleLabel) {

    /** Path of the class's detail page. */
    public String detailPath() {
        return "/classes/" + courseId;
    }
}
