package com.menta.virtual.application.dto;

import java.time.Instant;

/** Read model returned by all three progress use cases (US-VIRTUAL-005). */
public record LessonProgressView(String lessonId, int positionSeconds, boolean completed, Instant completedAt) {
}
