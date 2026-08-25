package com.menta.virtual.application.usecase;

import com.menta.shared.billing.CourseAccessSnapshot;
import com.menta.shared.billing.VirtualCourseEntitlementPort;
import com.menta.virtual.application.dto.LessonAccessDecision;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.Objects;
import java.util.UUID;

/**
 * Virtual-owned decision policy for public lesson access.
 *
 * <p>Billing supplies only the commercial facts needed at the last step; this
 * policy owns the ordered content rules. Local public rules always win in the
 * order free lesson, preview module, then a course absent from all plans.
 * Only a planned protected course can be granted by a current frozen Billing
 * snapshot. The decision deliberately carries no media identifier or signing
 * material, so callers must authorize before requesting a capability.</p>
 */
public final class LessonAccessPolicy {

    private final VirtualCourseEntitlementPort entitlementPort;

    public LessonAccessPolicy(VirtualCourseEntitlementPort entitlementPort) {
        this.entitlementPort = Objects.requireNonNull(entitlementPort, "entitlementPort cannot be null");
    }

    /**
     * Decides whether a caller can access a lesson without creating a media
     * capability. Anonymous callers never trigger a Billing entitlement read;
     * failures and malformed cross-module results fail closed.
     */
    public LessonAccessDecision decide(VirtualLesson lesson, VirtualModule module, UUID actingUserId) {
        Objects.requireNonNull(lesson, "lesson cannot be null");
        Objects.requireNonNull(module, "module cannot be null");

        if (lesson.isFree()) {
            return LessonAccessDecision.PUBLIC_FREE;
        }
        if (module.isPreview()) {
            return LessonAccessDecision.PUBLIC_MODULE_PREVIEW;
        }
        if (actingUserId == null) {
            return LessonAccessDecision.SUBSCRIPTION_REQUIRED;
        }

        try {
            CourseAccessSnapshot access = entitlementPort.resolveCourseAccess(
                actingUserId, lesson.getCourseId().getValue().toString()
            );
            if (access == null) {
                return LessonAccessDecision.SUBSCRIPTION_REQUIRED;
            }
            if (!access.courseInAnyPlan()) {
                return LessonAccessDecision.PUBLIC_UNPLANNED_COURSE;
            }
            return access.currentEntitlement()
                ? LessonAccessDecision.SUBSCRIPTION_GRANTED
                : LessonAccessDecision.SUBSCRIPTION_REQUIRED;
        } catch (RuntimeException unavailable) {
            return LessonAccessDecision.SUBSCRIPTION_REQUIRED;
        }
    }
}
