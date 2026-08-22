package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when an unauthenticated visitor asks for a premium (non-free)
 * lesson (US-VIRTUAL-003). The handler maps it to a 403
 * {@code application/problem+json}. A separate hierarchy from
 * {@link LessonNotFoundException}/{@link CourseNotOwnedException} so the
 * handler can pick a different HTTP status without disturbing the rest of
 * the public advice chain.
 *
 * <p>Note on the anti-enumeration discipline: this exception is only ever
 * thrown when the resource exists, is published, is not free, and the caller
 * has no identity at all. The {@link com.menta.virtual.application.usecase.GetPublicLessonUseCaseImpl}
 * collapses "missing lesson id" and "malformed lesson id" into
 * {@code Optional.empty()} first, so this exception can never leak the
 * existence / non-existence of either.</p>
 */
public class ForbiddenLessonAccessException extends BusinessException {

    private static final String ERROR_CODE = "LESSON_FORBIDDEN_SUBSCRIPTION_REQUIRED";

    public ForbiddenLessonAccessException() {
        super(ERROR_CODE, "This lesson requires an authenticated subscription.");
    }
}
