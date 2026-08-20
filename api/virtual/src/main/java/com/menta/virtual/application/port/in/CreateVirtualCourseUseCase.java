package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.CreateVirtualCourseCommand;
import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import java.util.UUID;

public interface CreateVirtualCourseUseCase {

    VirtualCourseManagementResult create(CreateVirtualCourseCommand command, UUID actingUserId, boolean actingAsAdmin);
}
