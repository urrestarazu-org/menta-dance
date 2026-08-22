package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.PublicLessonSubscriptionPrompt;

public record PublicLessonSubscriptionPromptDto(
    String message,
    String plansUrl
) {

    public static PublicLessonSubscriptionPromptDto from(PublicLessonSubscriptionPrompt prompt) {
        return new PublicLessonSubscriptionPromptDto(prompt.message(), prompt.plansUrl());
    }
}
