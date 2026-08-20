package com.menta.virtual.application.usecase;

import com.menta.virtual.application.port.in.DeleteVirtualCourseUseCase;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.CourseNotDraftException;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.UUID;

public class DeleteVirtualCourseUseCaseImpl implements DeleteVirtualCourseUseCase {

    private final VirtualCourseRepository courseRepository;
    private final VirtualModuleRepository moduleRepository;
    private final VirtualLessonRepository lessonRepository;
    private final VirtualCourseAuditRepository auditRepository;

    public DeleteVirtualCourseUseCaseImpl(
        VirtualCourseRepository courseRepository, VirtualModuleRepository moduleRepository,
        VirtualLessonRepository lessonRepository, VirtualCourseAuditRepository auditRepository
    ) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
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
        // Children first: virtual_lessons and virtual_modules both FK-reference
        // virtual_courses with no ON DELETE CASCADE — a draft with content
        // would otherwise fail the course delete with a constraint violation.
        // Lessons before modules: lessons also FK-reference virtual_modules.
        lessonRepository.deleteByCourseId(parsedCourseId);
        moduleRepository.deleteByCourseId(parsedCourseId);
        courseRepository.delete(parsedCourseId);
        auditRepository.append(
            parsedCourseId, actingUserId, "DELETE_COURSE", VirtualCourseResultMapper.toAuditSnapshot(course), null
        );
    }
}
