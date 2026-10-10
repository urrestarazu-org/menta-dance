package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PhysicalCourseDetail;
import com.menta.bff.application.port.out.CatalogApiClient;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link GetClassDetailViewUseCase}. Availability is decided
 * upstream (ADR-0028); this class only converts the UTC session instants to the
 * academy's display zone and words them in Spanish.
 */
@Slf4j
@RequiredArgsConstructor
public class GetClassDetailViewUseCaseImpl implements GetClassDetailViewUseCase {

    private final CatalogApiClient catalogApiClient;
    private final ZoneId displayZone;

    @Override
    public ClassDetailView execute(String courseId) {
        try {
            PhysicalCourseDetail course = catalogApiClient.getPhysicalCourse(courseId);
            return new ClassDetailView(
                    course.courseId(), course.title(), CatalogCard.levelLabel(course.level()), course.professorName(),
                    ClassLabels.schedule(course.dayOfWeek(), course.startTime()),
                    course.sessions().stream()
                            .map(s -> new ClassDetailView.SessionRow(
                                    s.sessionId(), ClassLabels.moment(s.scheduledAt(), displayZone),
                                    s.availableSpots()))
                            .toList(),
                    false);
        } catch (CatalogApiClient.ServiceUnavailableException e) {
            log.warn("Catalog unavailable, rendering degraded class detail: {}", e.getMessage());
            return ClassDetailView.unavailableView();
        }
    }
}
