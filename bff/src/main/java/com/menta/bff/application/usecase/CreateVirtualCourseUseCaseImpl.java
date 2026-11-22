package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CreateVirtualCourseCommand;
import com.menta.bff.application.dto.ManagedVirtualCourse;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link CreateVirtualCourseUseCase}: the adapter reads the level, so it cannot be missing. */
@RequiredArgsConstructor
public class CreateVirtualCourseUseCaseImpl implements CreateVirtualCourseUseCase {

    private final VirtualCourseManagementApiClient courseApiClient;

    @Override
    public ManagedVirtualCourse execute(String accessToken, CreateVirtualCourseCommand command) {
        String token = VirtualCourseSupport.token(accessToken);
        VirtualCourseSupport.required(VirtualCourseSupport.required(command).level());
        return courseApiClient.createCourse(token, command);
    }
}
