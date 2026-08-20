package com.menta.virtual.application.usecase;

import com.menta.virtual.application.port.in.DeleteVirtualCourseUseCase;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.exception.CourseNotDraftException;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.UUID;

public class DeleteVirtualCourseUseCaseImpl implements DeleteVirtualCourseUseCase {

    private final VirtualCourseRepository courseRepository;
    private final VirtualCourseAuditRepository auditRepository;

    public DeleteVirtualCourseUseCaseImpl(
        VirtualCourseRepository courseRepository, VirtualCourseAuditRepository auditRepository
    ) {
        this.courseRepository = courseRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public void delete(String courseId, UUID actingUserId, boolean actingAsAdmin) {
        CourseId parsedCourseId = CourseId.of(courseId);
        VirtualCourse course =
            CourseOwnershipGuard.resolveOwnedCourse(courseRepository, parsedCourseId, actingUserId, actingAsAdmin);
        if (!course.isDraft()) {
            throw new CourseNotDraftException();
        }
        courseRepository.delete(parsedCourseId);
        auditRepository.append(
            parsedCourseId, actingUserId, "DELETE_COURSE", VirtualCourseResultMapper.toAuditSnapshot(course), null
        );
    }
}
