package com.menta.bff.application.usecase;

/**
 * Use case for retrieving the render-ready view of a single lesson.
 * <p>
 * Orchestrates the 1 → 2 → (3) upstream call sequence from the design's Data
 * Flow section: course detail first (fail fast on catalog failure), then the
 * lesson detail (its {@code 403}/{@code 200} shape decides {@link
 * LessonView.Sample} vs proceeding), then conditionally the signed stream —
 * called for every granted lesson, free ones included, never skipped based on
 * {@code videoId}. The upstream access decision is rendered here, never
 * re-evaluated (design decision B; {@code virtual/spec.md} line 19).
 * </p>
 * <p>
 * Part of Clean Architecture application layer.
 * </p>
 */
public interface GetLessonViewUseCase {

    /**
     * Retrieves the render-ready view for the given lesson.
     *
     * @param courseId    course identifier, used to fetch course detail (never authenticated)
     * @param lessonId    lesson identifier
     * @param accessToken caller's access token, or {@code null} for an anonymous caller
     * @return {@link LessonView.Playable} when access is granted, {@link LessonView.Sample} when denied
     * @throws com.menta.bff.application.port.out.VirtualApiClient.NotFoundException
     *         if the course or lesson does not exist (404)
     * @throws com.menta.bff.application.port.out.VirtualApiClient.ServiceUnavailableException
     *         if any upstream call is unreachable (503), including a {@code /stream} failure after a granted lesson
     */
    LessonView execute(String courseId, String lessonId, String accessToken);
}
