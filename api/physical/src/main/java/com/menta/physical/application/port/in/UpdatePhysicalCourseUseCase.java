package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalCourseManagementResult;
import com.menta.physical.application.dto.UpdatePhysicalCourseCommand;
import java.util.UUID;

/** US-PHYSICAL-005 escenarios 3-5: partial update, with ownership and deactivation guards. */
public interface UpdatePhysicalCourseUseCase {

    PhysicalCourseManagementResult update(
        String courseId, UpdatePhysicalCourseCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
