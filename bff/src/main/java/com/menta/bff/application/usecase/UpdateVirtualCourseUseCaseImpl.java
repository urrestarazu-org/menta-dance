package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedVirtualCourse;
import com.menta.bff.application.dto.VirtualCourseChanges;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link UpdateVirtualCourseUseCase}. */
@RequiredArgsConstructor
public class UpdateVirtualCourseUseCaseImpl implements UpdateVirtualCourseUseCase {

    private final VirtualCourseManagementApiClient courseApiClient;

    @Override
    public ManagedVirtualCourse execute(String accessToken, String courseId, VirtualCourseChanges changes) {
        String token = VirtualCourseSupport.token(accessToken);
        String id = VirtualCourseSupport.courseId(courseId);
        return courseApiClient.updateCourse(token, id, VirtualCourseSupport.required(changes));
    }
}
