package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedVirtualCourse;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link PublishVirtualCourseUseCase}. */
@RequiredArgsConstructor
public class PublishVirtualCourseUseCaseImpl implements PublishVirtualCourseUseCase {

    private final VirtualCourseManagementApiClient courseApiClient;

    @Override
    public ManagedVirtualCourse execute(String accessToken, String courseId) {
        String token = VirtualCourseSupport.token(accessToken);
        return courseApiClient.publishCourse(token, VirtualCourseSupport.courseId(courseId));
    }
}
