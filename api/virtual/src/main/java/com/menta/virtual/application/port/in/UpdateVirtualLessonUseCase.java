package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.UpdateVirtualLessonCommand;
import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import java.util.UUID;

public interface UpdateVirtualLessonUseCase {

    VirtualLessonManagementResult update(
        String lessonId, UpdateVirtualLessonCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
