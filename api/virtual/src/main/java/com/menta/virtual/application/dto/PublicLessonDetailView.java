package com.menta.virtual.application.dto;

/**
 * Same-shape projection of a {@link com.menta.virtual.domain.model.VirtualLesson}
 * for the public detail endpoint. Two variants exist so the no-video-id
 * invariant on a free lesson is enforced at the type level — the free
 * record simply cannot carry a {@code videoId}, while the premium one can.
 *
 * <p>Module and course references are also carried as compact ref
 * records so the visitor side never round-trips for them.</p>
 */
public record PublicLessonDetailView(
    String lessonId,
    String title,
    String description,
    String duration,
    boolean isFree,
    int order,
    PublicModuleRef module,
    PublicCourseRef course,
    String videoId,
    String thumbnailUrl
) {

    /**
     * Variant constructor that drops {@code videoId}. Used for free
     * lessons (orchestrator contract: a free lesson never exposes its
     * Bunny.net reference to a visitor).
     */
    public static PublicLessonDetailView withoutVideoId(
        String lessonId, String title, String description, String duration, boolean isFree, int order,
        PublicModuleRef module, PublicCourseRef course, String thumbnailUrl
    ) {
        return new PublicLessonDetailView(
            lessonId, title, description, duration, isFree, order, module, course, null, thumbnailUrl
        );
    }

    /**
     * Variant constructor that carries {@code videoId}. Used for premium
     * lessons where the caller has an active entitlement — the Bunny.net
     * reference must surface so the streaming endpoint can issue a signed
     * URL later (US follow-up, out of scope for this PR).
     */
    public static PublicLessonDetailView withVideoId(
        String lessonId, String title, String description, String duration, boolean isFree, int order,
        PublicModuleRef module, PublicCourseRef course, String videoId, String thumbnailUrl
    ) {
        return new PublicLessonDetailView(
            lessonId, title, description, duration, isFree, order, module, course, videoId, thumbnailUrl
        );
    }
}
