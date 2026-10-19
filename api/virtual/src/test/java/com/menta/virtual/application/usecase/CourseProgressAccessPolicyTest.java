package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.shared.billing.CourseAccessSnapshot;
import com.menta.shared.billing.VirtualCourseEntitlementPort;
import com.menta.virtual.domain.model.CourseId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourseProgressAccessPolicyTest {

    private final VirtualCourseEntitlementPort entitlementPort = mock(VirtualCourseEntitlementPort.class);
    private final CourseProgressAccessPolicy policy = new CourseProgressAccessPolicy(entitlementPort);

    @Test
    void entitled_student_is_granted() {
        UUID userId = UUID.randomUUID();
        CourseId courseId = CourseId.generate();
        when(entitlementPort.resolveCourseAccess(userId, courseId.getValue().toString()))
            .thenReturn(new CourseAccessSnapshot(true, true));

        assertThat(policy.isGranted(courseId, userId)).isTrue();
    }

    @Test
    void no_entitlement_including_free_or_preview_only_progress_is_denied() {
        UUID userId = UUID.randomUUID();
        CourseId courseId = CourseId.generate();
        when(entitlementPort.resolveCourseAccess(userId, courseId.getValue().toString()))
            .thenReturn(new CourseAccessSnapshot(true, false));

        assertThat(policy.isGranted(courseId, userId)).isFalse();
    }

    @Test
    void lapsed_entitlement_is_denied() {
        UUID userId = UUID.randomUUID();
        CourseId courseId = CourseId.generate();
        when(entitlementPort.resolveCourseAccess(userId, courseId.getValue().toString()))
            .thenReturn(new CourseAccessSnapshot(false, false));

        assertThat(policy.isGranted(courseId, userId)).isFalse();
    }

    @Test
    void entitlement_port_failure_fails_closed_to_denied() {
        UUID userId = UUID.randomUUID();
        CourseId courseId = CourseId.generate();
        when(entitlementPort.resolveCourseAccess(userId, courseId.getValue().toString()))
            .thenThrow(new IllegalStateException("Billing unavailable"));

        assertThat(policy.isGranted(courseId, userId)).isFalse();
    }

    @Test
    void anonymous_caller_is_denied_even_with_a_planned_course() {
        CourseId courseId = CourseId.generate();
        when(entitlementPort.resolveCourseAccess(null, courseId.getValue().toString()))
            .thenReturn(new CourseAccessSnapshot(true, false));

        assertThat(policy.isGranted(courseId, null)).isFalse();
    }
}
