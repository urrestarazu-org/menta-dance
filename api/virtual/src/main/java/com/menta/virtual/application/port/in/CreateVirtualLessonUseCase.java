package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.CreateVirtualLessonCommand;
import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import java.util.UUID;

/** Inbound port for adding a lesson to a module (US-VIRTUAL-006). */
public interface CreateVirtualLessonUseCase {

    /**
     * Creates a lesson in the module and appends a {@code CREATE_LESSON} audit row.
     *
     * <p>An absent order defaults to the module's current lesson count. The module is authorized
     * through its parent course, so the failures are {@code ModuleNotFoundException}, {@code
     * CourseNotFoundException} and {@code CourseNotOwnedException}.
     */
    VirtualLessonManagementResult create(
        String moduleId, CreateVirtualLessonCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
