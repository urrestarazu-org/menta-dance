package com.menta.virtual.application.usecase;

import com.menta.shared.billing.CourseAccessSnapshot;
import com.menta.shared.billing.VirtualCourseEntitlementPort;
import com.menta.virtual.domain.model.CourseId;
import java.util.Objects;
import java.util.UUID;

/**
 * Course-aggregate access policy (US-VIRTUAL-005, Slice 3). Unlike {@link LessonAccessPolicy},
 * there is no free/preview exception here (design.md decision 5) — the course was already
 * resolved as {@code PUBLISHED} before this is consulted, so a direct entitlement check leaks
 * nothing new. Fails closed on any {@link RuntimeException} from the entitlement port.
 */
public final class CourseProgressAccessPolicy {

    private final VirtualCourseEntitlementPort entitlementPort;

    public CourseProgressAccessPolicy(VirtualCourseEntitlementPort entitlementPort) {
        this.entitlementPort = Objects.requireNonNull(entitlementPort, "entitlementPort cannot be null");
    }

    public boolean isGranted(CourseId courseId, UUID actingUserId) {
        try {
            CourseAccessSnapshot access =
                entitlementPort.resolveCourseAccess(actingUserId, courseId.getValue().toString());
            return access != null && actingUserId != null && access.currentEntitlement();
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
