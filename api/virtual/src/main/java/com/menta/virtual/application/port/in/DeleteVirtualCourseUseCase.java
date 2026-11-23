package com.menta.virtual.application.port.in;

import java.util.UUID;

/** Inbound port for deleting a course (US-VIRTUAL-006). */
public interface DeleteVirtualCourseUseCase {

    /**
     * Deletes a {@code DRAFT} course together with its lessons and modules, then appends a {@code
     * DELETE_COURSE} audit row holding the old snapshot.
     *
     * <p>A course in any other status fails with {@code CourseNotDraftException}, after the
     * existence and ownership checks. A {@code DRAFT} course whose students already have progress fails
     * with {@code CourseHasProgressException} (#834), and one that Billing still references in any plan or
     * subscription, historical ones included, fails with {@code CourseInUseException} (#856); in both
     * cases nothing is deleted nor audited.
     */
    void delete(String courseId, UUID actingUserId, boolean actingAsAdmin);
}
