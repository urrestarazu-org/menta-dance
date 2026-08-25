package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.shared.billing.CourseAccessSnapshot;
import com.menta.shared.billing.VirtualCourseEntitlementPort;
import com.menta.virtual.application.dto.LessonAccessDecision;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LessonAccessPolicyTest {

    private final VirtualCourseEntitlementPort entitlementPort = mock(VirtualCourseEntitlementPort.class);
    private final LessonAccessPolicy policy = new LessonAccessPolicy(entitlementPort);

    @Test
    void free_lesson_is_public_without_consulting_billing() {
        VirtualLesson lesson = lesson(true);

        assertThat(policy.decide(lesson, module(false), null)).isEqualTo(LessonAccessDecision.PUBLIC_FREE);

        verify(entitlementPort, never()).resolveCourseAccess(any(), anyString());
    }

    @Test
    void preview_module_is_public_without_consulting_billing() {
        VirtualLesson lesson = lesson(false);

        assertThat(policy.decide(lesson, module(true), null))
            .isEqualTo(LessonAccessDecision.PUBLIC_MODULE_PREVIEW);

        verify(entitlementPort, never()).resolveCourseAccess(any(), anyString());
    }

    @Test
    void unplanned_course_is_public_without_consulting_billing() {
        UUID userId = UUID.randomUUID();
        VirtualLesson lesson = lesson(false);
        when(entitlementPort.resolveCourseAccess(eq(userId), eq(lesson.getCourseId().getValue().toString())))
            .thenReturn(new CourseAccessSnapshot(false, false));

        assertThat(policy.decide(lesson, module(false), userId))
            .isEqualTo(LessonAccessDecision.PUBLIC_UNPLANNED_COURSE);
    }

    @Test
    void planned_course_with_current_entitlement_is_granted() {
        UUID userId = UUID.randomUUID();
        VirtualLesson lesson = lesson(false);
        when(entitlementPort.resolveCourseAccess(eq(userId), eq(lesson.getCourseId().getValue().toString())))
            .thenReturn(new CourseAccessSnapshot(true, true));

        assertThat(policy.decide(lesson, module(false), userId))
            .isEqualTo(LessonAccessDecision.SUBSCRIPTION_GRANTED);
    }

    @Test
    void planned_course_without_identity_is_denied_without_consulting_billing() {
        assertThat(policy.decide(lesson(false), module(false), null))
            .isEqualTo(LessonAccessDecision.SUBSCRIPTION_REQUIRED);

        verify(entitlementPort, never()).resolveCourseAccess(any(), anyString());
    }

    @Test
    void planned_course_without_entitlement_or_unavailable_billing_is_denied() {
        UUID userId = UUID.randomUUID();
        VirtualLesson lesson = lesson(false);
        when(entitlementPort.resolveCourseAccess(eq(userId), eq(lesson.getCourseId().getValue().toString())))
            .thenReturn(new CourseAccessSnapshot(true, false));

        assertThat(policy.decide(lesson, module(false), userId))
            .isEqualTo(LessonAccessDecision.SUBSCRIPTION_REQUIRED);

        when(entitlementPort.resolveCourseAccess(eq(userId), eq(lesson.getCourseId().getValue().toString())))
            .thenThrow(new IllegalStateException("Billing unavailable"));

        assertThat(policy.decide(lesson, module(false), userId))
            .isEqualTo(LessonAccessDecision.SUBSCRIPTION_REQUIRED);
    }

    private static VirtualLesson lesson(boolean free) {
        CourseId courseId = CourseId.generate();
        return new VirtualLesson(
            com.menta.virtual.domain.model.LessonId.generate(), ModuleId.generate(), courseId,
            "Lesson", "Description", "video-id", 10, free, 1
        );
    }

    private static VirtualModule module(boolean preview) {
        return new VirtualModule(ModuleId.generate(), CourseId.generate(), "Module", preview, 1);
    }
}
