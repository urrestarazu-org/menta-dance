package com.menta.billing.application.usecase;

import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.shared.billing.VirtualCourseEntitlementPort;
import java.time.Instant;
import java.util.UUID;

/** Billing implementation of Virtual's read-only entitlement contract (ADR-0039). */
public final class VirtualCourseEntitlementService implements VirtualCourseEntitlementPort {

    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public VirtualCourseEntitlementService(SubscriptionRepository subscriptionRepository, Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    @Override
    public boolean hasActiveEntitlement(UUID userId, String courseId) {
        if (userId == null || courseId == null || courseId.isBlank()) {
            return false;
        }
        Instant now = clock.now();
        return subscriptionRepository.findCurrentByUserId(userId)
            .filter(subscription -> subscription.grantsAccess())
            .filter(subscription -> subscription.getEndDate().filter(end -> end.isAfter(now)).isPresent())
            .filter(subscription -> subscription.getCourseIds().contains(courseId))
            .isPresent();
    }
}
