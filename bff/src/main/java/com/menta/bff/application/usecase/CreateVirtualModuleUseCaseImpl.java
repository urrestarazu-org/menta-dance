package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CreateVirtualModuleCommand;
import com.menta.bff.application.dto.ManagedVirtualModule;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link CreateVirtualModuleUseCase}. */
@RequiredArgsConstructor
public class CreateVirtualModuleUseCaseImpl implements CreateVirtualModuleUseCase {

    private final VirtualCourseManagementApiClient courseApiClient;

    @Override
    public ManagedVirtualModule execute(String accessToken, String courseId, CreateVirtualModuleCommand command) {
        String token = VirtualCourseSupport.token(accessToken);
        String id = VirtualCourseSupport.courseId(courseId);
        return courseApiClient.createModule(token, id, VirtualCourseSupport.required(command));
    }
}
