package com.menta.bff.application.dto;

import java.util.List;

/**
 * A virtual course with its modules and lessons, as the admin detail reports it. It prefills the edit form and
 * feeds the module and lesson screens; read only. Same fields as {@link ManagedVirtualCourse}, except that the
 * image is called {@code thumbnailUrl}, as in the API.
 */
public record VirtualCourseDetail(
        String courseId, String title, String shortDescription, String description, String professorId,
        String thumbnailUrl, String category, VirtualCourseLevel level, boolean premium, VirtualCourseStatus status,
        List<VirtualModuleDetail> modules, VirtualCourseStats stats) {

    /** A module of the course. {@code preview} modules serve their lessons without a subscription. */
    public record VirtualModuleDetail(
            String moduleId, String title, boolean preview, int order, List<VirtualLessonDetail> lessons) {
    }

    /** A lesson of a module; {@code videoId} is {@code null} until a video is assigned. */
    public record VirtualLessonDetail(
            String lessonId, String title, String description, int durationMinutes, boolean free, int order,
            String videoId) {
    }

    /** Totals of the course; {@code totalDuration} is worded by the API, e.g. {@code "2h 30m"}. */
    public record VirtualCourseStats(int moduleCount, int lessonCount, String totalDuration) {
    }
}
