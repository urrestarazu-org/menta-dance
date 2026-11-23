package com.menta.virtual.application.usecase;

import com.menta.shared.billing.VirtualCourseReferencePort;
import com.menta.virtual.application.port.in.DeleteVirtualCourseUseCase;
import com.menta.virtual.application.port.out.LessonProgressRepository;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.CourseHasProgressException;
import com.menta.virtual.domain.exception.CourseInUseException;
import com.menta.virtual.domain.exception.CourseNotDraftException;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Default {@link DeleteVirtualCourseUseCase}, wrapped by its transactional decorator in {@code
 * VirtualConfiguration}, so the writes and the audit row commit together.
 *
 * <p>A course whose students already have progress is not deleted (#834): a published course can go
 * back to {@code DRAFT}, so the draft-only rule does not rule out student history, and the progress rows
 * reference the lessons and the course with RESTRICT foreign keys (V19). The check runs before any write
 * and after the ownership and status checks, so a published course keeps answering {@code
 * COURSE_NOT_DRAFT}; nothing is deleted and no audit row is written when it rejects.</p>
 *
 * <p>A course that Billing references is not deleted either (#856): plans and subscriptions point at the
 * course by value, with no foreign key, so the database would let the delete through and leave them
 * dangling. The question goes through the shared {@link VirtualCourseReferencePort}, never to Billing's
 * tables, and any reference counts, historical ones included. It is asked last, after the progress
 * check.</p>
 */
@RequiredArgsConstructor
public class DeleteVirtualCourseUseCaseImpl implements DeleteVirtualCourseUseCase {

    private final VirtualCourseRepository courseRepository;
    private final VirtualModuleRepository moduleRepository;
    private final VirtualLessonRepository lessonRepository;
    private final LessonProgressRepository progressRepository;
    private final VirtualCourseReferencePort courseReferencePort;
    private final VirtualCourseAuditRepository auditRepository;

    @Override
    public void delete(String courseId, UUID actingUserId, boolean actingAsAdmin) {
        CourseId parsedCourseId = CourseId.of(courseId);
        VirtualCourse course =
            CourseOwnershipGuard.resolveOwnedCourse(courseRepository, parsedCourseId, actingUserId, actingAsAdmin);
        if (!course.isDraft()) {
            throw new CourseNotDraftException();
        }
        if (progressRepository.existsByCourseId(parsedCourseId.getValue())) {
            throw new CourseHasProgressException();
        }
        if (courseReferencePort.isReferenced(parsedCourseId.getValue().toString())) {
            throw new CourseInUseException();
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
