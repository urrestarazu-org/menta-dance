package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedVirtualModule;
import com.menta.bff.application.dto.VirtualModuleChanges;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link UpdateVirtualModuleUseCase}. */
@RequiredArgsConstructor
public class UpdateVirtualModuleUseCaseImpl implements UpdateVirtualModuleUseCase {

    private final VirtualCourseManagementApiClient courseApiClient;

    @Override
    public ManagedVirtualModule execute(String accessToken, String moduleId, VirtualModuleChanges changes) {
        String token = VirtualCourseSupport.token(accessToken);
        String id = VirtualCourseSupport.moduleId(moduleId);
        return courseApiClient.updateModule(token, id, VirtualCourseSupport.required(changes));
    }
}
