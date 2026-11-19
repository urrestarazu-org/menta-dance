package com.menta.virtual.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Wire request for {@code PUT /api/v1/virtual/lessons/{lessonId}/progress}. The position is
 * mandatory: a body without it must be rejected instead of silently saving {@code 0} over the
 * stored position (#790). The range check stays in the domain ({@code LessonProgress#withPosition}).
 */
public record SaveLessonProgressRequest(@NotNull Integer positionSeconds) {
}
