package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.PublicLessonStreamResult;
import java.util.UUID;

/**
 * Use case for resolving a public lesson's signed streaming URL
 * (US-VIRTUAL-004). Returns a sealed
 * {@link PublicLessonStreamResult} so the controller can map each
 * outcome to a different HTTP status without reaching back into the
 * domain.
 *
 * <p>Two distinct outcomes — and only two — anchor the contract:</p>
 * <ul>
 *   <li>{@link PublicLessonStreamResult.Authorized} — the lesson
 *       resolves, the caller has an active entitlement to the parent
 *       course (for non-free lessons). The controller returns
 *       {@code 200 OK} with the stream/lesson JSON.</li>
 *   <li>{@link PublicLessonStreamResult.AccessDenied} — the lesson
 *       resolves but the caller has no active entitlement. The
 *       controller returns {@code 403 Forbidden} with the
 *       {@link com.menta.virtual.application.dto.LessonAccessDecisionDto}
 *       block already used by the public detail endpoint (#48).</li>
 * </ul>
 *
 * <p>What this contract deliberately does NOT have:</p>
 * <ul>
 *   <li>{@code Optional.empty()} for "lesson not found" / "id
 *       malformed" / "parent course unpublished". A missing id in a
 *       public-context signature is indistinguishable from a malformed
 *       one, and the orchestrator's spec is that the controller
 *       throws {@link com.menta.virtual.domain.exception.LessonNotFoundException}
 *       directly so {@link com.menta.virtual.infrastructure.web.controller.VirtualPublicLessonExceptionHandler}
 *       can produce the same RFC 9457 404 for both. Free lessons
 *       collapse here too — the orchestrator decided a free lesson
 *       produces the same access-decision {@code AccessDenied}, so the
 *       second branch above is the rejection pathway for any caller
 *       whose entitlement is not proven. The decision keeps the use
 *       case small without leaking the {@code isFree} flag to the
 *       streaming endpoint.</li>
 *   <li>a separate "expired subscription" outcome for US-VIRTUAL-004
 *       escenario 2. The orchestrator's document budgeted that to a
 *       follow-up; the current cross-module
 *       {@link com.menta.shared.billing.VirtualCourseEntitlementPort}
 *       returns a boolean and the MVP speaks {@code SUBSCRIPTION_REQUIRED}
 *       whenever the entitlement is missing, regardless of cause.</li>
 * </ul>
 */
public interface GetPublicLessonStreamUseCase {

    /**
     * Resolve a public lesson's streaming URL.
     *
     * @param lessonId the opaque lesson id from the path variable.
     *     Malformed values, ids that parse but resolve to no row, and
     *     ids whose parent course is not published all collapse to a
     *     thrown {@code LessonNotFoundException} so the controller can
     *     apply anti-enumeration.
     * @param actingUserId {@code null} when the request reached the
     *     controller anonymously. {@code Free} lessons are allowed to
     *     play with {@code null} (the MVP gate is "no entitlement", and
     *     anonymous callers simply have no entitlement by definition);
     *     non-free lessons require an authenticated user with
     *     active entitlement.
     * @return a sealed {@link PublicLessonStreamResult}. Never
     *     {@code null}.
     */
    PublicLessonStreamResult get(String lessonId, UUID actingUserId);
}
