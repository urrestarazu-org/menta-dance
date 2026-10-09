package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.CatalogApiClient;
import com.menta.bff.domain.model.CourseModality;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link GetClassesViewUseCase}. The upstream list has no
 * modality parameter, so the physical filter is applied here.
 */
@Slf4j
@RequiredArgsConstructor
public class GetClassesViewUseCaseImpl implements GetClassesViewUseCase {

    private final CatalogApiClient catalogApiClient;

    @Override
    public ClassesView execute() {
        try {
            return new ClassesView(catalogApiClient.listCourses().stream()
                    .filter(course -> course.modality() == CourseModality.PHYSICAL)
                    .map(course -> new ClassCard(
                            course.courseId(), course.title(), CatalogCard.levelLabel(course.level()),
                            course.professorName(), ClassLabels.schedule(course.dayOfWeek(), course.startTime())))
                    .toList(), false);
        } catch (CatalogApiClient.ServiceUnavailableException e) {
            log.warn("Catalog unavailable, rendering degraded classes list: {}", e.getMessage());
            return ClassesView.unavailableView();
        }
    }
}
