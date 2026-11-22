package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedVirtualLesson;
import com.menta.bff.application.dto.VirtualLessonChanges;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link UpdateVirtualLessonUseCase}. */
@RequiredArgsConstructor
public class UpdateVirtualLessonUseCaseImpl implements UpdateVirtualLessonUseCase {

    private final VirtualCourseManagementApiClient courseApiClient;

    @Override
    public ManagedVirtualLesson execute(String accessToken, String lessonId, VirtualLessonChanges changes) {
        String token = VirtualCourseSupport.token(accessToken);
        String id = VirtualCourseSupport.lessonId(lessonId);
        return courseApiClient.updateLesson(token, id, VirtualCourseSupport.required(changes));
    }
}
