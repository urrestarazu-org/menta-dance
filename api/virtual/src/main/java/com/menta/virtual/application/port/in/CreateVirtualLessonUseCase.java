package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.CreateVirtualLessonCommand;
import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import java.util.UUID;

public interface CreateVirtualLessonUseCase {

    VirtualLessonManagementResult create(
        String moduleId, CreateVirtualLessonCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
