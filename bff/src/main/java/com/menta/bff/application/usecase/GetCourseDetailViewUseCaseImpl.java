package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CourseDetail;
import com.menta.bff.application.dto.CourseProgress;
import com.menta.bff.application.port.out.VirtualApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link GetCourseDetailViewUseCase}.
 * <p>
 * Runs the Data Flow sequence from design.md: catalog first, unauthenticated
 * and untranslated on failure (#170 unchanged); then, only when a caller
 * token is present, course progress — wrapped in a broad guard that
 * collapses every failure and every unresolvable state to {@link
 * CourseDetailView.Plain} (design D4). Progress is supplementary
 * personalization whose absence yields the already-shipped, valid anonymous
 * rendering; catalog is the page's own content and is the only call allowed
 * to fail the page.
 * </p>
 */
public class GetCourseDetailViewUseCaseImpl implements GetCourseDetailViewUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetCourseDetailViewUseCaseImpl.class);

    private final VirtualApiClient virtualApiClient;

    /**
     * Constructor for dependency injection.
     *
     * @param virtualApiClient HTTP client for the Virtual/Catalog API
     */
    public GetCourseDetailViewUseCaseImpl(VirtualApiClient virtualApiClient) {
        this.virtualApiClient = Objects.requireNonNull(virtualApiClient, "virtualApiClient cannot be null");
    }

    @Override
    public CourseDetailView execute(String courseId, String accessToken) {
        Objects.requireNonNull(courseId, "courseId cannot be null");

        // Step 1: catalog, never authenticated. NotFoundException/
        // ServiceUnavailableException propagate untranslated and are NOT
        // caught by the progress guard below — catalog is the page's own
        // content and can still fail the page (design D4).
        CourseDetail course = virtualApiClient.getCourseDetail(courseId);

        if (accessToken == null) {
            return new CourseDetailView.Plain(course);
        }

        // Step 2: progress, conditionally attempted, never allowed to fail
        // the page. Every RuntimeException here — 403, 404, the unmapped
        // 401, and 503 alike — collapses to Plain, logged at WARN.
        CourseProgress progress;
        try {
            progress = virtualApiClient.getCourseProgress(courseId, accessToken);
        } catch (RuntimeException progressFailure) {
            log.warn("Course progress unavailable for course {}: {}", courseId, progressFailure.getMessage());
            return new CourseDetailView.Plain(course);
        }

        CourseProgress.ResumeLesson resumeLesson = progress.resumeLesson();
        if (resumeLesson == null) {
            // Zero progress or a zero-lesson course — the aggregate produces
            // the identical shape for both and this page must not attempt
            // to distinguish them.
            return new CourseDetailView.Plain(course);
        }

        String title = findLessonTitle(course, resumeLesson.lessonId());
        if (title == null) {
            // Resume lessonId does not resolve against the catalog
            // projection (currently unreachable per spec's note, since
            // api:virtual has no lesson-deletion capability) — degrade
            // safely to Plain instead of throwing.
            return new CourseDetailView.Plain(course);
        }

        CourseDetailView.Resume resume = new CourseDetailView.Resume(
                resumeLesson.lessonId(), title, progress.percentage(), progress.completedLessons(), progress.totalLessons());
        return new CourseDetailView.Resumable(course, resume);
    }

    /**
     * Cross-references {@code lessonId} against the flattened lesson list,
     * reusing the flatten pattern from {@code GetLessonViewUseCaseImpl}. Unlike
     * that use case's {@code indexOfLesson}, this lookup is null-safe rather
     * than throwing: the id here comes from a second upstream call (progress)
     * whose view of the course can in principle drift from the catalog's,
     * so a miss must degrade to {@link CourseDetailView.Plain}, not raise.
     */
    private static String findLessonTitle(CourseDetail course, String lessonId) {
        List<CourseDetail.Lesson> lessons = course.modules().stream()
                .flatMap(module -> module.lessons().stream())
                .toList();
        for (CourseDetail.Lesson lesson : lessons) {
            if (lesson.lessonId().equals(lessonId)) {
                return lesson.title();
            }
        }
        return null;
    }
}
