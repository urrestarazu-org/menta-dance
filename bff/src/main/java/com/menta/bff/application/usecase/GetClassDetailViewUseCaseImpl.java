package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PhysicalCourseDetail;
import com.menta.bff.application.dto.PhysicalCoursePricing;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.CatalogApiClient;
import com.menta.bff.application.port.out.PhysicalSessionsApiClient;
import java.time.ZoneId;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link GetClassDetailViewUseCase}. Availability is decided
 * upstream (ADR-0028); this class only converts the UTC session instants to the
 * academy's display zone and words them in Spanish. The monthly price comes from Billing and
 * never breaks the page: a missing or unreadable price only changes {@link ClassDetailView#priceState()}.
 *
 * <p>For a signed-in student, the sessions they already hold are marked {@code owned} (#542). The
 * mark is decided upstream by {@code sessions/me} and only decorates the page: when that lookup
 * fails the sessions render exactly as for an anonymous visitor.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class GetClassDetailViewUseCaseImpl implements GetClassDetailViewUseCase {

    private final CatalogApiClient catalogApiClient;
    private final BillingApiClient billingApiClient;
    private final PhysicalSessionsApiClient physicalSessionsApiClient;
    private final ZoneId displayZone;

    @Override
    public ClassDetailView execute(String courseId, String accessToken) {
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
            Set<String> ownedSessionIds = ownedSessionIds(course, accessToken);
            return new ClassDetailView(
                    course.courseId(), course.title(), CatalogCard.levelLabel(course.level()), course.professorName(),
                    ClassLabels.schedule(course.dayOfWeek(), course.startTime()), priceState, priceLabel,
                    course.sessions().stream()
                            .map(s -> new ClassDetailView.SessionRow(
                                    s.sessionId(), ClassLabels.moment(s.scheduledAt(), displayZone),
                                    s.availableSpots(), ownedSessionIds.contains(s.sessionId())))
                            .toList(),
                    false);
        } catch (CatalogApiClient.ServiceUnavailableException e) {
            log.warn("Catalog unavailable, rendering degraded class detail: {}", e.getMessage());
            return ClassDetailView.unavailableView();
        }
    }

    /** Ids of the listed sessions the student holds; empty for a visitor or when the lookup fails. */
    private Set<String> ownedSessionIds(PhysicalCourseDetail course, String accessToken) {
        if (accessToken == null || accessToken.isBlank() || course.sessions().isEmpty()) {
            return Set.of();
        }
        try {
            return physicalSessionsApiClient.findMySessionIds(accessToken);
        } catch (PhysicalSessionsApiClient.ServiceUnavailableException e) {
            log.warn("Student sessions unavailable, rendering class detail without marks: {}", e.getMessage());
            return Set.of();
        }
    }
}
