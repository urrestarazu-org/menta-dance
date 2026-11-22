package com.menta.bff.application.dto;

/**
 * A virtual course as the management API reports it ({@code VirtualCourseManagementResponse}).
 */
public record ManagedVirtualCourse(
        String courseId, String title, String shortDescription, String description, String professorId,
        String imageUrl, String category, VirtualCourseLevel level, boolean premium, VirtualCourseStatus status) {
}
