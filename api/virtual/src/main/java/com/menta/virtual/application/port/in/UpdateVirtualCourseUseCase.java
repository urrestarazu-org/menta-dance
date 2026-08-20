package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.UpdateVirtualCourseCommand;
import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import java.util.UUID;

public interface UpdateVirtualCourseUseCase {

    VirtualCourseManagementResult update(
        String courseId, UpdateVirtualCourseCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
