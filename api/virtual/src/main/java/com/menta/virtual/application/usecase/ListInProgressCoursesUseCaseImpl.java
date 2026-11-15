package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.CourseProgressView;
import com.menta.virtual.application.dto.InProgressCourseView;
import com.menta.virtual.application.port.in.ListInProgressCoursesUseCase;
import com.menta.virtual.application.port.out.InProgressCourseRowProjection;
import com.menta.virtual.application.port.out.LessonProgressRepository;
import com.menta.virtual.domain.exception.InvalidInProgressCoursesLimitException;
import com.menta.virtual.domain.model.CourseId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Default {@link ListInProgressCoursesUseCase} (#765). Exactly two bounded repository reads,
 * whatever the number of courses: every progress row of the user in a published course (already
 * in recency order) and one grouped lesson count for those courses. Each course then goes through
 * {@link CourseProgressAssembler}, so completed lessons, percentage and resume lesson are the ones
 * {@code GET /courses/{courseId}/progress} would answer.
 *
 * <p>Access uses {@link CourseProgressAccessPolicy}, the rule of the per-course progress. The
 * entitlement port only answers one course at a time, so it is asked once per candidate course,
 * lazily: completed courses are discarded before asking, and the loop stops as soon as {@code
 * limit} courses are collected. A course without access is skipped instead of failing, and does
 * not use up the limit.</p>
 */
@RequiredArgsConstructor
public class ListInProgressCoursesUseCaseImpl implements ListInProgressCoursesUseCase {

    private static final int COMPLETE = 100;

    private final LessonProgressRepository progressRepository;
    private final CourseProgressAccessPolicy accessPolicy;

    @Override
    public List<InProgressCourseView> list(UUID actingUserId, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidInProgressCoursesLimitException(limit, MAX_LIMIT);
        }
        List<InProgressCourseRowProjection> rows = progressRepository.findRowsForUserInPublishedCourses(actingUserId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<InProgressCourseRowProjection>> rowsByCourse = groupInIncomingOrder(rows);
        Map<UUID, Long> totals = progressRepository.countLessonsByCourseIds(rowsByCourse.keySet());

        List<InProgressCourseView> courses = new ArrayList<>();
        for (Map.Entry<UUID, List<InProgressCourseRowProjection>> course : rowsByCourse.entrySet()) {
            if (courses.size() == limit) {
                break;
            }
            UUID courseId = course.getKey();
            List<InProgressCourseRowProjection> courseRows = course.getValue();
            CourseProgressView progress =
                CourseProgressAssembler.assemble(courseId.toString(), courseRows, totals.getOrDefault(courseId, 0L));
            if (progress.percentage() < COMPLETE && accessPolicy.isGranted(CourseId.of(courseId), actingUserId)) {
                courses.add(toView(progress, courseRows.get(0)));
            }
        }
        return courses;
    }

    private static Map<UUID, List<InProgressCourseRowProjection>> groupInIncomingOrder(
        List<InProgressCourseRowProjection> rows
    ) {
        Map<UUID, List<InProgressCourseRowProjection>> rowsByCourse = new LinkedHashMap<>();
        for (InProgressCourseRowProjection row : rows) {
            rowsByCourse.computeIfAbsent(row.getCourseId(), ignored -> new ArrayList<>()).add(row);
        }
        return rowsByCourse;
    }

    /**
     * {@code resumeRow} is the first row of the course, the one {@link CourseProgressAssembler}
     * picks as resume lesson; it also carries the titles.
     */
    private static InProgressCourseView toView(CourseProgressView progress, InProgressCourseRowProjection resumeRow) {
        CourseProgressView.ResumeLesson resume = progress.resumeLesson();
        return new InProgressCourseView(
            progress.courseId(), resumeRow.getCourseTitle(), progress.completedLessons(), progress.totalLessons(),
            progress.percentage(),
            new InProgressCourseView.ResumeLesson(
                resume.lessonId(), resume.moduleId(), resumeRow.getLessonTitle(), resume.positionSeconds()
            )
        );
    }
}
