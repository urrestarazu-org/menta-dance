package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.UpdateVirtualCourseCommand;
import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import java.util.UUID;

/** Inbound port for editing a course (US-VIRTUAL-006). */
public interface UpdateVirtualCourseUseCase {

    /**
     * Applies a partial update: only the fields present in the command change, with category and
     * level parsed from their string form. Appends an {@code UPDATE_COURSE} audit row with the
     * before and after snapshots.
     */
    VirtualCourseManagementResult update(
        String courseId, UpdateVirtualCourseCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
