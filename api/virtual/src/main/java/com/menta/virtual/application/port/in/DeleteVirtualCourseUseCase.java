package com.menta.virtual.application.port.in;

import java.util.UUID;

public interface DeleteVirtualCourseUseCase {

    void delete(String courseId, UUID actingUserId, boolean actingAsAdmin);
}
