package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link ListCourseSessionsUseCase}. The backend works in UTC only (ADR-0048): the local days
 * become the half-open UTC range {@code [start of localFrom, start of the day after localTo)}. A session that
 * starts at the end of the range, which the API may or may not include, is left out, so the screen shows the
 * same days whatever the API does at the edge.
 */
@RequiredArgsConstructor
public class ListCourseSessionsUseCaseImpl implements ListCourseSessionsUseCase {

    private final PhysicalCourseManagementApiClient courseApiClient;
    private final ZoneId displayZone;

    @Override
    public List<ManagedSessionView> execute(
            String accessToken, String courseId, LocalDate localFrom, LocalDate localTo) {
        String token = CourseManagementSupport.token(accessToken);
        String id = CourseManagementSupport.courseId(courseId);
        Instant from = localFrom == null ? null : localFrom.atStartOfDay(displayZone).toInstant();
        Instant to = localTo == null ? null : localTo.plusDays(1).atStartOfDay(displayZone).toInstant();
        return courseApiClient.listCourseSessions(token, id, from, to).stream()
                .filter(session -> to == null || session.scheduledAt().isBefore(to))
                .map(session -> CourseManagementSupport.view(session, displayZone))
                .toList();
    }
}
