package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.VirtualCourseDetail.VirtualModuleDetail;
import com.menta.bff.application.dto.VirtualLessonLocation;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException.Code;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link GetManagedVirtualLessonUseCase}. */
@RequiredArgsConstructor
public class GetManagedVirtualLessonUseCaseImpl implements GetManagedVirtualLessonUseCase {

    private final VirtualCourseManagementApiClient courseApiClient;

    @Override
    public VirtualLessonLocation execute(String accessToken, String courseId, String moduleId, String lessonId) {
        String token = VirtualCourseSupport.token(accessToken);
        String course = VirtualCourseSupport.courseId(courseId);
        String module = VirtualCourseSupport.moduleId(moduleId);
        String lesson = VirtualCourseSupport.lessonId(lessonId);
        VirtualModuleDetail found = courseApiClient.getManagedCourse(token, course).modules().stream()
                .filter(candidate -> candidate.moduleId().equals(module)).findFirst()
                .orElseThrow(() -> new VirtualCourseManagementException(Code.MODULE_NOT_FOUND));
        return found.lessons().stream().filter(candidate -> candidate.lessonId().equals(lesson)).findFirst()
                .map(match -> new VirtualLessonLocation(course, module, found.title(), match))
                .orElseThrow(() -> new VirtualCourseManagementException(Code.LESSON_NOT_FOUND));
    }
}
