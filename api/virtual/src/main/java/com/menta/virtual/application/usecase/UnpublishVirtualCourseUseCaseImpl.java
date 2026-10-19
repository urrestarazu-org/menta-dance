package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.in.UnpublishVirtualCourseUseCase;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.UUID;

/**
 * Unpublishing only flips {@code status} back to {@code DRAFT}. Whether an
 * already-subscribed student keeps temporary access after this is
 * entitlement logic that belongs to #56 (US-VIRTUAL-007), not to course
 * management — this use case has no way to know about Billing subscriptions
 * and shouldn't.
 */
public class UnpublishVirtualCourseUseCaseImpl implements UnpublishVirtualCourseUseCase {

    private final VirtualCourseRepository courseRepository;
    private final VirtualCourseAuditRepository auditRepository;

    public UnpublishVirtualCourseUseCaseImpl(
        VirtualCourseRepository courseRepository, VirtualCourseAuditRepository auditRepository
    ) {
        this.courseRepository = courseRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public VirtualCourseManagementResult unpublish(String courseId, UUID actingUserId, boolean actingAsAdmin) {
        VirtualCourse course = CourseOwnershipGuard.resolveOwnedCourse(
            courseRepository, CourseId.of(courseId), actingUserId, actingAsAdmin
        );
        String before = VirtualCourseResultMapper.toAuditSnapshot(course);
        VirtualCourse saved = courseRepository.save(course.unpublish());
        auditRepository.append(
            saved.getId(), actingUserId, "UNPUBLISH_COURSE", before, VirtualCourseResultMapper.toAuditSnapshot(saved)
        );
        return VirtualCourseResultMapper.toResult(saved);
    }
}
