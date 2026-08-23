package com.menta.virtual.application.dto;

/**
 * Sealed parent for the two outcomes of the public lesson-stream use case
 * (US-VIRTUAL-004). The contract was a deliberate sealed shape rather
 * than {@code Optional}s or two separate methods because the controller
 * must map each outcome to a different HTTP status with a different
 * response body, and a sealed type makes that mapping exhaustive at
 * compile time.
 *
 * <p>The orchestrator's spec called for three branches on the public
 * surface (free / premium-with-entitlement / premium-requires-sub), but
 * a "stream" endpoint is only meaningful for premium lessons a user
 * can actually play — we collapse "free" into "no stream" upstream
 * (the {@code GET /lessons/{id}} endpoint already exposes the body a
 * free visitor needs), so the only two outcomes here are
 * {@link Authorized} and {@link AccessDenied}.</p>
 *
 * <p>{@link AccessDenied} carries a {@link LessonAccessDecisionDto} so
 * the BFF / frontend can render the same access-decision UX the public
 * detail endpoint already uses (#48) — no new wire contract, only a
 * different HTTP status.</p>
 */
public sealed interface PublicLessonStreamResult
    permits PublicLessonStreamResult.Authorized,
            PublicLessonStreamResult.AccessDenied {

    /**
     * Resolved lesson + signed stream ready to play. The controller
     * maps this to {@code 200 OK} with the stream/lesson JSON.
     */
    record Authorized(PublicLessonStreamView view) implements PublicLessonStreamResult {
    }

    /**
     * The lesson resolves fine but the caller is not entitled to play
     * its stream yet. The controller maps this to {@code 403 Forbidden}
     * with the {@link LessonAccessDecisionDto} as the body, mirroring
     * the access-decision shape already exposed by the
     * {@code GET /lessons/{id}} endpoint (#48).
     */
    record AccessDenied(LessonAccessDecisionDto access) implements PublicLessonStreamResult {
    }
}
