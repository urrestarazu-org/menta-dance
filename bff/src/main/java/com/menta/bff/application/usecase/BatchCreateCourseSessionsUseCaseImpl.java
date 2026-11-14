package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedCourse;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException.Code;
import com.menta.bff.application.usecase.ClassLabels.DateRange;
import com.menta.bff.application.usecase.ClassLabels.WeeklySlot;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link BatchCreateCourseSessionsUseCase}. The API generates one session per UTC date whose UTC
 * weekday is the course's, so the range is converted with the course's own start time: the course is read first
 * (there is no endpoint for one course, so it is looked up in the list the caller sees), which also keeps the
 * caller from sending a time that disagrees with the course.
 */
@RequiredArgsConstructor
public class BatchCreateCourseSessionsUseCaseImpl implements BatchCreateCourseSessionsUseCase {

    private final PhysicalCourseManagementApiClient courseApiClient;
    private final ZoneId displayZone;

    @Override
    public List<ManagedSessionView> execute(
            String accessToken, String courseId, LocalDate localFrom, LocalDate localTo) {
        String token = CourseManagementSupport.token(accessToken);
        String id = CourseManagementSupport.courseId(courseId);
        ManagedCourse course = courseApiClient.listCourses(token).stream()
                .filter(candidate -> id.equals(candidate.courseId()))
                .findFirst()
                .orElseThrow(() -> new CourseManagementException(Code.COURSE_NOT_FOUND));
        WeeklySlot local = ClassLabels.toLocalSlot(course.dayOfWeek(), course.startTime(), displayZone);
        DateRange utc = ClassLabels.toUtcDateRange(localFrom, localTo, local.time(), displayZone);
        return courseApiClient.createSessionsInBatch(token, id, utc.from(), utc.to()).stream()
                .map(session -> CourseManagementSupport.view(session, displayZone))
                .toList();
    }
}
