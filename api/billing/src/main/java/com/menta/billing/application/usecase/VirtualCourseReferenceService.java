package com.menta.billing.application.usecase;

import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.shared.billing.VirtualCourseReferencePort;
import lombok.RequiredArgsConstructor;

/**
 * Billing implementation of Virtual's course-reference check (#856).
 *
 * <p>A course is referenced when any plan includes it (active or not) or when the frozen course
 * snapshot of any subscription (active, cancelled or expired) includes it. A virtual payment cites its
 * plan, never the course, so it needs no query of its own.</p>
 */
@RequiredArgsConstructor
public final class VirtualCourseReferenceService implements VirtualCourseReferencePort {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Override
    public boolean isReferenced(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            return false;
        }
        return planRepository.existsByCourseId(courseId) || subscriptionRepository.existsByCourseId(courseId);
    }
}
