package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PhysicalCourseDetail;
import com.menta.bff.application.dto.PhysicalCoursePricing;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.CatalogApiClient;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link GetClassDetailViewUseCase}. Availability is decided
 * upstream (ADR-0028); this class only converts the UTC session instants to the
 * academy's display zone and words them in Spanish. The monthly price comes from Billing and
 * never breaks the page: a missing or unreadable price only changes {@link ClassDetailView#priceState()}.
 */
@Slf4j
@RequiredArgsConstructor
public class GetClassDetailViewUseCaseImpl implements GetClassDetailViewUseCase {

    private final CatalogApiClient catalogApiClient;
    private final BillingApiClient billingApiClient;
    private final ZoneId displayZone;

    @Override
    public ClassDetailView execute(String courseId) {
        try {
            PhysicalCourseDetail course = catalogApiClient.getPhysicalCourse(courseId);
            ClassDetailView.PriceState priceState = ClassDetailView.PriceState.PUBLISHED;
            String priceLabel = null;
            try {
                PhysicalCoursePricing pricing = billingApiClient.getPhysicalCoursePricing(courseId);
                priceLabel = ClassLabels.amount(pricing.monthlyPrice(), pricing.currency()) + " por mes";
            } catch (BillingApiClient.NotFoundException e) {
                priceState = ClassDetailView.PriceState.NOT_PUBLISHED;
            } catch (BillingApiClient.ServiceUnavailableException e) {
                log.warn("Billing unavailable, rendering class detail without price: {}", e.getMessage());
                priceState = ClassDetailView.PriceState.UNAVAILABLE;
            }
            return new ClassDetailView(
                    course.courseId(), course.title(), CatalogCard.levelLabel(course.level()), course.professorName(),
                    ClassLabels.schedule(course.dayOfWeek(), course.startTime()), priceState, priceLabel,
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
