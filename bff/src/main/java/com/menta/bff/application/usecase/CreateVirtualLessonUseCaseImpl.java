package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CreateVirtualLessonCommand;
import com.menta.bff.application.dto.ManagedVirtualLesson;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link CreateVirtualLessonUseCase}. */
@RequiredArgsConstructor
public class CreateVirtualLessonUseCaseImpl implements CreateVirtualLessonUseCase {

    private final VirtualCourseManagementApiClient courseApiClient;

    @Override
    public ManagedVirtualLesson execute(String accessToken, String moduleId, CreateVirtualLessonCommand command) {
        String token = VirtualCourseSupport.token(accessToken);
        String id = VirtualCourseSupport.moduleId(moduleId);
        return courseApiClient.createLesson(token, id, VirtualCourseSupport.required(command));
    }
}
