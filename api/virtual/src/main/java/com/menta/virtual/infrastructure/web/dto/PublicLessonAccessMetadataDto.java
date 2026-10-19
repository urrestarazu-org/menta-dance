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

    /**
     * Metadata for a lesson served as a public preview: {@code preview} is {@code true} and no
     * subscription is required.
     */
    public static PublicLessonAccessMetadataDto publicPreview() {
        return new PublicLessonAccessMetadataDto(true, false);
    }

    /**
     * Metadata for a lesson served as full paid access: {@code preview} and {@code
     * requiresSubscription} are both {@code false}.
     */
    public static PublicLessonAccessMetadataDto entitled() {
        return new PublicLessonAccessMetadataDto(false, false);
    }
}
