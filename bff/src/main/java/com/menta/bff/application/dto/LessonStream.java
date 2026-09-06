package com.menta.bff.application.dto;

/**
 * Placeholder wire shape for {@code GET /api/v1/virtual/lessons/{lessonId}/stream}.
 * <p>
 * Exists in this PR only so {@code VirtualApiClient.getStream} compiles;
 * {@code VirtualApiAdapter.getStream} is stubbed with
 * {@code UnsupportedOperationException} until PR 1b, which replaces this
 * record with the full shape ({@code url}, {@code expiresAt}).
 * </p>
 */
public record LessonStream() {
}
