package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedVirtualModule;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link ReorderVirtualModulesUseCase}. */
@RequiredArgsConstructor
public class ReorderVirtualModulesUseCaseImpl implements ReorderVirtualModulesUseCase {

    private final VirtualCourseManagementApiClient courseApiClient;

    @Override
    public List<ManagedVirtualModule> execute(String accessToken, String courseId, List<String> moduleIds) {
        String token = VirtualCourseSupport.token(accessToken);
        String id = VirtualCourseSupport.courseId(courseId);
        return courseApiClient.reorderModules(token, id, VirtualCourseSupport.moduleIds(moduleIds));
    }
}
