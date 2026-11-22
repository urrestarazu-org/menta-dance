package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link DeleteVirtualLessonUseCase}. */
@RequiredArgsConstructor
public class DeleteVirtualLessonUseCaseImpl implements DeleteVirtualLessonUseCase {

    private final VirtualCourseManagementApiClient courseApiClient;

    @Override
    public void execute(String accessToken, String lessonId) {
        String token = VirtualCourseSupport.token(accessToken);
        courseApiClient.deleteLesson(token, VirtualCourseSupport.lessonId(lessonId));
    }
}
