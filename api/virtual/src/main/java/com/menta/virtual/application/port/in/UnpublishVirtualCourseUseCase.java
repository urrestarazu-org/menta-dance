package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import java.util.UUID;

public interface UnpublishVirtualCourseUseCase {

    VirtualCourseManagementResult unpublish(String courseId, UUID actingUserId, boolean actingAsAdmin);
}
