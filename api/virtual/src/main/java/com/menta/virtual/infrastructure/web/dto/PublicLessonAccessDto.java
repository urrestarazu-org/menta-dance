package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.LessonAccessDecisionDto;

/**
 * Wire-level mirror of {@link LessonAccessDecisionDto}; the spec exposes
 * the {@code access} block as a sibling of {@code lesson} in the JSON
 * response body.
 */
public record PublicLessonAccessDto(
    boolean allowed,
    String reason,
    String message,
    String plansUrl
) {

    public static PublicLessonAccessDto from(LessonAccessDecisionDto dto) {
        return new PublicLessonAccessDto(dto.allowed(), dto.reason(), dto.message(), dto.plansUrl());
    }
}
