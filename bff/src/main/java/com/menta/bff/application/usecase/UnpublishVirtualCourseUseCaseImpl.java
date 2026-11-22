package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedVirtualCourse;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link UnpublishVirtualCourseUseCase}. */
@RequiredArgsConstructor
public class UnpublishVirtualCourseUseCaseImpl implements UnpublishVirtualCourseUseCase {

    private final VirtualCourseManagementApiClient courseApiClient;

    @Override
    public ManagedVirtualCourse execute(String accessToken, String courseId) {
        String token = VirtualCourseSupport.token(accessToken);
        return courseApiClient.unpublishCourse(token, VirtualCourseSupport.courseId(courseId));
    }
}
