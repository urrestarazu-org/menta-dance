package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import java.util.List;
import java.util.UUID;

/** Inbound port for listing the courses an actor manages (US-VIRTUAL-006). */
public interface ListManagedVirtualCoursesUseCase {

    /** Lists every course for an admin and only the actor's own courses otherwise, regardless of their status. */
    List<VirtualCourseManagementResult> list(UUID actingUserId, boolean actingAsAdmin);
}
