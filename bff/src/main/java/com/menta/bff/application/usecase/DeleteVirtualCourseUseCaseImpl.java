package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link DeleteVirtualCourseUseCase}. */
@RequiredArgsConstructor
public class DeleteVirtualCourseUseCaseImpl implements DeleteVirtualCourseUseCase {

    private final VirtualCourseManagementApiClient courseApiClient;

    @Override
    public void execute(String accessToken, String courseId) {
        String token = VirtualCourseSupport.token(accessToken);
        courseApiClient.deleteCourse(token, VirtualCourseSupport.courseId(courseId));
    }
}
