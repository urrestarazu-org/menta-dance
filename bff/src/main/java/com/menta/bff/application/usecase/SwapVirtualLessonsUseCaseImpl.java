package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.VirtualLessonChanges;
import com.menta.bff.application.dto.VirtualLessonPosition;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException.Code;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link SwapVirtualLessonsUseCase}. */
@RequiredArgsConstructor
public class SwapVirtualLessonsUseCaseImpl implements SwapVirtualLessonsUseCase {

    private final VirtualCourseManagementApiClient courseApiClient;

    @Override
    public void execute(String accessToken, VirtualLessonPosition lesson, VirtualLessonPosition neighbour) {
        String token = VirtualCourseSupport.token(accessToken);
        VirtualLessonPosition moving = VirtualCourseSupport.required(lesson);
        VirtualLessonPosition other = VirtualCourseSupport.required(neighbour);
        String lessonId = VirtualCourseSupport.lessonId(moving.lessonId());
        String neighbourId = VirtualCourseSupport.lessonId(other.lessonId());
        if (lessonId.equals(neighbourId)) {
            throw new VirtualCourseManagementException(Code.INVALID_REQUEST);
        }
        courseApiClient.updateLesson(token, lessonId, onlyOrder(other.order()));
        try {
            courseApiClient.updateLesson(token, neighbourId, onlyOrder(moving.order()));
        } catch (VirtualCourseManagementException | ServiceUnavailableException e) {
            putBack(token, lessonId, moving.order());
            throw e;
        }
    }

    /** Best effort: two lessons must not be left with the same order, but the failure to report is the first one. */
    private void putBack(String token, String lessonId, int order) {
        try {
            courseApiClient.updateLesson(token, lessonId, onlyOrder(order));
        } catch (VirtualCourseManagementException | ServiceUnavailableException ignored) {
            // The API is not answering: the caller is told about the update that failed, which is the cause.
        }
    }

    private static VirtualLessonChanges onlyOrder(int order) {
        return new VirtualLessonChanges(null, null, null, null, null, order);
    }
}
