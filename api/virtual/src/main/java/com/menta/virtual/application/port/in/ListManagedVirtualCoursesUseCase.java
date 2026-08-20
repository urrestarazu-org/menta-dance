package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import java.util.List;
import java.util.UUID;

public interface ListManagedVirtualCoursesUseCase {

    List<VirtualCourseManagementResult> list(UUID actingUserId, boolean actingAsAdmin);
}
