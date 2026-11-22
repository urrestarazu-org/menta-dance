package com.menta.bff.application.dto;

/**
 * A lesson and the {@code order} it holds in its module right now. A swap of two lessons is described by the two
 * positions that trade places.
 */
public record VirtualLessonPosition(String lessonId, int order) {
}
