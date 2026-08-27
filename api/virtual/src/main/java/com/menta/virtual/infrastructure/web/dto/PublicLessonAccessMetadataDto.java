package com.menta.virtual.infrastructure.web.dto;

/**
 * Capability-safe access metadata returned with successful lesson detail.
 *
 * <p>{@code preview} distinguishes the public preview shape from full paid
 * access, while {@code requiresSubscription} remains explicit for clients
 * without turning a media identifier or stream URL into authorization data.</p>
 */
public record PublicLessonAccessMetadataDto(
    boolean preview,
    boolean requiresSubscription
) {

    public static PublicLessonAccessMetadataDto publicPreview() {
        return new PublicLessonAccessMetadataDto(true, false);
    }

    public static PublicLessonAccessMetadataDto entitled() {
        return new PublicLessonAccessMetadataDto(false, false);
    }
}
