package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.CourseProgressView;
import com.menta.virtual.application.port.in.GetCourseProgressUseCase;
import com.menta.virtual.application.port.out.CourseProgressRowProjection;
import com.menta.virtual.application.port.out.LessonProgressRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.exception.CourseNotFoundException;
import com.menta.virtual.domain.exception.ForbiddenCourseProgressException;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.List;
import java.util.UUID;

/**
 * Default {@link GetCourseProgressUseCase} (US-VIRTUAL-005, Slice 3). Exactly two bounded
 * repository reads once the course resolves as PUBLISHED and the caller is entitled
 * (design.md "Course aggregate: two bounded queries, no N+1"): {@code countLessonsByCourseId}
 * for the live denominator and {@code findRowsForUserAndCourse} for the already-ordered
 * resume/percentage input. Neither read runs before the course is found and access is granted.
 *
 * <p>A completed lesson that later became inaccessible is never filtered out here — it still
 * counts toward {@code completedLessons} (design.md decision 6): the progress row is never
 * deleted when a lesson's own accessibility changes, and this use case never re-checks
 * per-lesson access once course-level entitlement is granted.</p>
 */
public class GetCourseProgressUseCaseImpl implements GetCourseProgressUseCase {

    private final VirtualCourseRepository courseRepository;
    private final LessonProgressRepository progressRepository;
    private final CourseProgressAccessPolicy accessPolicy;

    public GetCourseProgressUseCaseImpl(
        VirtualCourseRepository courseRepository, LessonProgressRepository progressRepository,
        CourseProgressAccessPolicy accessPolicy
    ) {
        this.courseRepository = courseRepository;
        this.progressRepository = progressRepository;
        this.accessPolicy = accessPolicy;
    }

    @Override
    public CourseProgressView get(String courseId, UUID actingUserId) {
        CourseId id = parseCourseIdOrThrow(courseId);
        VirtualCourse course = courseRepository.findPublishedById(id).orElseThrow(CourseNotFoundException::new);
        if (!accessPolicy.isGranted(course.getId(), actingUserId)) {
            throw new ForbiddenCourseProgressException();
        }

        UUID courseUuid = course.getId().getValue();
        long totalLessons = progressRepository.countLessonsByCourseId(courseUuid);
        List<CourseProgressRowProjection> rows = progressRepository.findRowsForUserAndCourse(actingUserId, courseUuid);
        return CourseProgressAssembler.assemble(course.getId().toString(), rows, totalLessons);
    }

    private static CourseId parseCourseIdOrThrow(String rawCourseId) {
        if (rawCourseId == null || rawCourseId.isBlank()) {
            throw new CourseNotFoundException();
        }
        try {
            return CourseId.of(rawCourseId);
        } catch (IllegalArgumentException malformed) {
            throw new CourseNotFoundException();
        }
    }
}
