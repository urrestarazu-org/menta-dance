package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.PublicLessonView;
import java.util.Optional;
import java.util.UUID;

/**
 * Public read of a single published virtual lesson (US-VIRTUAL-003). The
 * exact response shape (free / premium-with-entitlement /
 * premium-requires-subscription) is decided inside the use case; the
 * controller translates it into the matching REST response and never
 * reaches back into the domain to branch again.
 *
 * <p>The contract deliberately returns {@code Optional.empty()} in three
 * indistinguishable cases — the id was malformed (not a UUID), the id
 * parsed but no row matches, or the parent course is not published. The
 * application layer abstracts over all three so the web layer can apply a
 * single anti-enumeration discipline.</p>
 *
 * <p>The caller identity is optional: visitors come in without one and
 * still need to read free lessons. Anonymous callers against a premium
 * lesson are turned into
 * {@link com.menta.virtual.domain.exception.ForbiddenLessonAccessException}
 * by the implementation — NOT returned as an empty view — because the
 * caller status matters for the HTTP status decision (403 vs 200) and
 * the caller is uniquely positioned at the controller boundary to make
 * it.</p>
 */
public interface GetPublicLessonUseCase {

    /**
     * Resolve a public lesson view.
     *
     * @param lessonId the opaque {@code lessonId} from the path variable.
     *     Malformed values collapse to {@code Optional.empty()} — the
     *     controller maps that to a 404 {@link com.menta.virtual.domain.exception.LessonNotFoundException}.
     * @param actingUserId {@code null} when the request reached the
     *     controller anonymously. When present it is forwarded to
     *     billing's entitlement check.
     * @return the {@link PublicLessonView} for the requested lesson, or
     *     {@code Optional.empty()} when the lesson cannot be resolved.
     * @throws com.menta.virtual.domain.exception.ForbiddenLessonAccessException
     *     only when the lesson exists, the parent course is published, the
     *     lesson is not free, AND {@code actingUserId == null}. The
     *     authenticated-but-no-entitlement case is reported via the
     *     {@code REQUIRES_SUBSCRIPTION} view instead, so the caller sees a
     *     preview rather than a hard 403.
     */
    Optional<PublicLessonView> get(String lessonId, UUID actingUserId);
}
