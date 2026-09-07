package com.menta.bff.application.usecase;

/**
 * Use case for retrieving a course detail page's full rendering decision,
 * including optional "Continuar" personalization for an authenticated,
 * entitled visitor with resumable progress.
 * <p>
 * Replaces {@link GetCourseDetailUseCase} (design D2): the return type is no
 * longer plain {@code CourseDetail} because this use case now also decides
 * whether — and how — to personalize the page, a decision that used to have
 * no home at all.
 * </p>
 * <p>
 * Part of Clean Architecture application layer.
 * </p>
 */
public interface GetCourseDetailViewUseCase {

    /**
     * Retrieves the course detail view for the given course and caller.
     * <p>
     * The catalog call is always made and its failures propagate untranslated
     * (design D4 — catalog is the page's own content). The progress call is
     * made only when {@code accessToken} is non-null, and every failure or
     * unresolvable state on that call collapses to {@link CourseDetailView.Plain}
     * — progress is supplementary and can never fail the page.
     * </p>
     *
     * @param courseId    course identifier
     * @param accessToken caller's access token, or {@code null} for an anonymous caller
     * @return the resolved course detail view
     * @throws com.menta.bff.application.port.out.VirtualApiClient.NotFoundException
     *         if the course does not exist or is not published (404)
     * @throws com.menta.bff.application.port.out.VirtualApiClient.ServiceUnavailableException
     *         if the catalog upstream is unreachable (503)
     */
    CourseDetailView execute(String courseId, String accessToken);
}
