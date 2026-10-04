package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalCourseManagementResult;
import com.menta.physical.application.dto.UpdatePhysicalCourseCommand;
import java.util.UUID;

/** US-PHYSICAL-005 escenarios 3-5: partial update, with ownership and deactivation guards. */
public interface UpdatePhysicalCourseUseCase {

    /**
     * Applies the present fields of the command to a course; absent fields stay untouched.
     *
     * <p>Fails with {@code CourseNotFoundException} for an unknown course, with {@code
     * CourseNotOwnedException} when a non-admin caller is not the owner, and with {@code
     * CourseHasActiveAssignmentsException} when deactivating a course that still has a future
     * session with a confirmed assignment.
     */
    PhysicalCourseManagementResult update(
        String courseId, UpdatePhysicalCourseCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
