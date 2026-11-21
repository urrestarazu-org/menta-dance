package com.menta.bff.application.dto;

/**
 * What the create form submits. {@code professorId} is for an administrator: an instructor's own id comes from
 * the token and sending another one is refused by the API.
 */
public record CreateVirtualCourseCommand(
        String title, String shortDescription, String description, String imageUrl, String category,
        VirtualCourseLevel level, String professorId) {
}
