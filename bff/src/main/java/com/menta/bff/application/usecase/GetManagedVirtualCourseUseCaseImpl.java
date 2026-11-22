package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.VirtualCourseDetail;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link GetManagedVirtualCourseUseCase}. */
@RequiredArgsConstructor
public class GetManagedVirtualCourseUseCaseImpl implements GetManagedVirtualCourseUseCase {

    private final VirtualCourseManagementApiClient courseApiClient;

    @Override
    public VirtualCourseDetail execute(String accessToken, String courseId) {
        String token = VirtualCourseSupport.token(accessToken);
        return courseApiClient.getManagedCourse(token, VirtualCourseSupport.courseId(courseId));
    }
}
