package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import java.util.UUID;

/** Inbound port for taking a course out of the public catalog (US-VIRTUAL-006). */
public interface UnpublishVirtualCourseUseCase {

    /** Moves the course back to {@code DRAFT} and appends an {@code UNPUBLISH_COURSE} audit row. */
    VirtualCourseManagementResult unpublish(String courseId, UUID actingUserId, boolean actingAsAdmin);
}
