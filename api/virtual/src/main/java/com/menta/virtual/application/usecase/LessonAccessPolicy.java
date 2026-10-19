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
 * order free lesson, then preview module. Every other lesson is protected and
 * is granted only by a current frozen Billing entitlement for the caller — a
 * course absent from every plan is a commercial configuration gap, not a
 * grant, so it falls through to the same entitlement check as any planned
 * course and denies by default (see ADR-0041). The decision deliberately
 * carries no media identifier or signing material, so callers must authorize
 * before requesting a capability.</p>
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
        try {
            CourseAccessSnapshot access = entitlementPort.resolveCourseAccess(
                actingUserId, lesson.getCourseId().getValue().toString()
            );
            if (access == null || actingUserId == null) {
                return LessonAccessDecision.SUBSCRIPTION_REQUIRED;
            }
            return access.currentEntitlement()
                ? LessonAccessDecision.SUBSCRIPTION_GRANTED
                : LessonAccessDecision.SUBSCRIPTION_REQUIRED;
        } catch (RuntimeException unavailable) {
            return LessonAccessDecision.SUBSCRIPTION_REQUIRED;
        }
    }
}
