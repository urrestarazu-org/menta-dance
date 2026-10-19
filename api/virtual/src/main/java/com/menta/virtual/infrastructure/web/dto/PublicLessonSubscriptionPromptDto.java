package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.PublicLessonSubscriptionPrompt;

/** Wire form of the upsell prompt attached to a free lesson. */
public record PublicLessonSubscriptionPromptDto(
    String message,
    String plansUrl
) {

    /** Maps the application prompt to its wire form. */
    public static PublicLessonSubscriptionPromptDto from(PublicLessonSubscriptionPrompt prompt) {
        return new PublicLessonSubscriptionPromptDto(prompt.message(), prompt.plansUrl());
    }
}
