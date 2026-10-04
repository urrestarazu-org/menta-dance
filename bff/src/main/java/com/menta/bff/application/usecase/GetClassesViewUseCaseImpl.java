package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.PhysicalCatalogApiClient;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link GetClassesViewUseCase}: the catalog's physical
 * courses, labelled for display. Failures propagate untranslated to the
 * shared error view.
 */
@RequiredArgsConstructor
public class GetClassesViewUseCaseImpl implements GetClassesViewUseCase {

    private final PhysicalCatalogApiClient physicalCatalogApiClient;

    @Override
    public List<ClassCard> execute() {
        return physicalCatalogApiClient.listClasses().stream()
                .map(course -> new ClassCard(
                        course.courseId(), course.title(), ClassLabels.level(course.level()),
                        course.professorName(), ClassLabels.schedule(course.dayOfWeek(), course.startTime()),
                        course.capacity()))
                .toList();
    }
}
