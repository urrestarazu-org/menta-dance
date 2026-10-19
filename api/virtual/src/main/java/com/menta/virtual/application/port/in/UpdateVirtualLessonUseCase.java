package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.UpdateVirtualLessonCommand;
import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import java.util.UUID;

/** Inbound port for editing a lesson (US-VIRTUAL-006). */
public interface UpdateVirtualLessonUseCase {

    /**
     * Applies a partial update to the lesson, authorized through its parent course, and appends an
     * {@code UPDATE_LESSON} audit row with the before and after snapshots. An unknown lesson fails
     * with {@code LessonNotFoundException}.
     */
    VirtualLessonManagementResult update(
        String lessonId, UpdateVirtualLessonCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
