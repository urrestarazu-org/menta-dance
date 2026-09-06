package com.menta.bff.application.dto;

/**
 * Placeholder wire shape for {@code GET /api/v1/virtual/lessons/{lessonId}}.
 * <p>
 * Exists in this PR only so {@code VirtualApiClient.getLesson} compiles;
 * {@code VirtualApiAdapter.getLesson} is stubbed with
 * {@code UnsupportedOperationException} until PR 1b, which replaces this
 * record with the full nested shape ({@code CourseRef}/{@code ModuleRef},
 * nullable {@code videoId}, {@code Nav navigation}).
 * </p>
 */
public record LessonDetail() {
}
