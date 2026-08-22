package com.menta.virtual.application.dto;

/**
 * Outcome classification for the public lesson read (US-VIRTUAL-003). The
 * {@link PublicLessonView} tree collapses to the same three buckets the
 * controller surfaces — they map 1:1 to {@code
 * com.menta.virtual.infrastructure.web.dto.PublicLessonResponse}'s
 * permitted types. Kept in {@code application.dto} (not {@code domain})
 * because it is a presentation/cross-layer signal returned by a use case,
 * not a domain invariant.
 */
public enum LessonAccessDecision {
    /** The lesson is free; render the full free view. No entitlement ever consulted. */
    FREE,
    /** The lesson is premium and the caller has an active entitlement; render the full premium view including {@code videoId}. */
    PREMIUM_ACCESSIBLE,
    /** The lesson is premium and the caller has no active entitlement; render the preview-with-prompt view (HTTP 200, NOT 403). */
    REQUIRES_SUBSCRIPTION
}
