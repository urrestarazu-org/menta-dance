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
    /** A lesson explicitly marked free is public, regardless of commercial state. */
    PUBLIC_FREE,
    /** A premium lesson is public because its containing module is a preview. */
    PUBLIC_MODULE_PREVIEW,
    /** A protected course has a current frozen-snapshot entitlement for this caller. */
    SUBSCRIPTION_GRANTED,
    /** A protected lesson has no current entitlement and must not expose media (see ADR-0041). */
    SUBSCRIPTION_REQUIRED
}
