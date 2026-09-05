package com.menta.virtual.infrastructure.web.dto;

/** Wire request for {@code PUT /api/v1/virtual/lessons/{lessonId}/progress}. */
public record SaveLessonProgressRequest(int positionSeconds) {
}
