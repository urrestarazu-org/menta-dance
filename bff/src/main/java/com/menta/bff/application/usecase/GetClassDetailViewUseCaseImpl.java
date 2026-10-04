package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ClassPricing;
import com.menta.bff.application.dto.PhysicalClassDetail;
import com.menta.bff.application.port.out.PhysicalCatalogApiClient;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link GetClassDetailViewUseCase}.
 *
 * <p>The catalog is read first so an unknown id answers 404 without touching
 * billing. Session starts arrive in UTC and are labelled in the academy zone,
 * which is configuration rather than a constant so a second venue in another
 * zone needs no code change.
 * </p>
 */
@RequiredArgsConstructor
public class GetClassDetailViewUseCaseImpl implements GetClassDetailViewUseCase {

    private final PhysicalCatalogApiClient physicalCatalogApiClient;
    private final PhysicalPurchaseApiClient physicalPurchaseApiClient;
    private final ZoneId displayZone;

    @Override
    public ClassDetailView execute(String courseId) {
        PhysicalClassDetail course = physicalCatalogApiClient.getClassDetail(courseId);
        ClassPricing pricing = physicalPurchaseApiClient.findPricing(courseId).orElse(null);

        return new ClassDetailView(
                course.courseId(),
                course.title(),
                ClassLabels.level(course.level()),
                course.professorName(),
                ClassLabels.schedule(course.dayOfWeek(), course.startTime()),
                pricing,
                course.sessions().stream()
                        .map(session -> new ClassDetailView.SessionRow(
                                session.sessionId(),
                                ClassLabels.sessionStart(session.scheduledAt(), displayZone),
                                session.availableSpots()))
                        .toList());
    }
}
