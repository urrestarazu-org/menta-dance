package com.menta.billing.application.usecase;

import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.SubscriptionStatus;
import com.menta.shared.billing.CourseAccessSnapshot;
import com.menta.shared.billing.VirtualCourseEntitlementPort;
import java.time.Instant;
import java.util.UUID;

/** Billing implementation of Virtual's read-only course-access contract (ADR-0039). */
public final class VirtualCourseEntitlementService implements VirtualCourseEntitlementPort {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public VirtualCourseEntitlementService(
        PlanRepository planRepository, SubscriptionRepository subscriptionRepository, Clock clock
    ) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    @Override
    public CourseAccessSnapshot resolveCourseAccess(UUID userIdOrNull, String courseId) {
        if (courseId == null || courseId.isBlank()) {
            return new CourseAccessSnapshot(false, false);
        }
        boolean courseInAnyPlan = planRepository.findAllActiveOrderByPriceAsc().stream()
            .anyMatch(plan -> plan.courseIds().contains(courseId));
        if (userIdOrNull == null) {
            return new CourseAccessSnapshot(courseInAnyPlan, false);
        }
        Instant now = clock.now();
        boolean currentEntitlement = subscriptionRepository.findAllByUserId(userIdOrNull).stream()
            .anyMatch(subscription -> grantsCurrentAccess(subscription, courseId, now));
        return new CourseAccessSnapshot(courseInAnyPlan, currentEntitlement);
    }

    private static boolean grantsCurrentAccess(Subscription subscription, String courseId, Instant now) {
        return subscription.getFulfillmentStatus() == FulfillmentStatus.ASSIGNED
            && (subscription.getStatus() == SubscriptionStatus.ACTIVE
                || subscription.getStatus() == SubscriptionStatus.CANCELLED)
            && subscription.getEndDate().filter(end -> end.isAfter(now)).isPresent()
            && subscription.getCourseIds().contains(courseId);
    }
}
