package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CourseDetail;
import com.menta.bff.application.dto.LessonDetail;
import com.menta.bff.application.dto.LessonStream;
import com.menta.bff.application.dto.LessonSummary;
import com.menta.bff.application.dto.Nav;
import com.menta.bff.application.port.out.VirtualApiClient;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Implementation of {@link GetLessonViewUseCase}.
 * <p>
 * Runs the 1 → 2 → (3) sequence from design's Data Flow section: course
 * detail first, letting {@code NotFoundException}/{@code
 * ServiceUnavailableException} short-circuit before any lesson or stream
 * call; then the lesson detail, whose {@code ForbiddenException} (bare 403,
 * no body) is the only branch that produces {@link LessonView.Sample}
 * instead of proceeding; then the stream call, run unconditionally for
 * every granted ({@code 200}) lesson — free or entitled — because a granted
 * response never carries a playable URL itself, and {@code videoId == null}
 * on a free lesson only means the raw id is withheld, not that no stream
 * exists.
 * </p>
 */
public class GetLessonViewUseCaseImpl implements GetLessonViewUseCase {

    private final VirtualApiClient virtualApiClient;

    /**
     * Constructor for dependency injection.
     *
     * @param virtualApiClient HTTP client for the Virtual/Catalog API
     */
    public GetLessonViewUseCaseImpl(VirtualApiClient virtualApiClient) {
        this.virtualApiClient = Objects.requireNonNull(virtualApiClient, "virtualApiClient cannot be null");
    }

    @Override
    public LessonView execute(String courseId, String lessonId, String accessToken) {
        Objects.requireNonNull(courseId, "courseId cannot be null");
        Objects.requireNonNull(lessonId, "lessonId cannot be null");

        // Step 1: course detail, never authenticated. NotFoundException/
        // ServiceUnavailableException propagate untranslated and short-circuit
        // before any lesson/stream call — the controller decides how to render.
        CourseDetail course = virtualApiClient.getCourseDetail(courseId);

        // Step 2: lesson detail, conditionally authenticated. A bare 403 is the
        // only branch that renders a Sample instead of proceeding to step 3.
        LessonDetail lesson;
        try {
            lesson = virtualApiClient.getLesson(lessonId, accessToken);
        } catch (VirtualApiClient.ForbiddenException denied) {
            return buildSample(course, lessonId);
        }

        // Step 3: signed stream, run for every granted lesson (free or
        // entitled) — never skipped based on videoId. A ServiceUnavailableException
        // here propagates untranslated; the controller renders the error view
        // rather than a partial player.
        LessonStream stream = virtualApiClient.getStream(lessonId, accessToken);
        return new LessonView.Playable(course, lesson, stream.url(), lesson.navigation());
    }

    private LessonView.Sample buildSample(CourseDetail course, String lessonId) {
        List<CourseDetail.Lesson> lessons = flattenLessons(course);
        int index = indexOfLesson(lessons, lessonId);
        LessonSummary summary = toLessonSummary(lessons.get(index));
        Nav nav = deriveNav(lessons, index);
        return new LessonView.Sample(course, summary, nav);
    }

    private static List<CourseDetail.Lesson> flattenLessons(CourseDetail course) {
        return course.modules().stream()
                .flatMap(module -> module.lessons().stream())
                .toList();
    }

    private static int indexOfLesson(List<CourseDetail.Lesson> lessons, String lessonId) {
        for (int i = 0; i < lessons.size(); i++) {
            if (lessons.get(i).lessonId().equals(lessonId)) {
                return i;
            }
        }
        throw new NoSuchElementException("Lesson not found in course detail: " + lessonId);
    }

    private static LessonSummary toLessonSummary(CourseDetail.Lesson lesson) {
        return new LessonSummary(lesson.lessonId(), lesson.title(), lesson.duration(), lesson.isFree(), lesson.order());
    }

    private static Nav deriveNav(List<CourseDetail.Lesson> lessons, int index) {
        Nav.Ref previous = index > 0 ? toRef(lessons.get(index - 1)) : null;
        Nav.Ref next = index < lessons.size() - 1 ? toRef(lessons.get(index + 1)) : null;
        return new Nav(previous, next);
    }

    private static Nav.Ref toRef(CourseDetail.Lesson lesson) {
        return new Nav.Ref(lesson.lessonId(), lesson.title());
    }
}
