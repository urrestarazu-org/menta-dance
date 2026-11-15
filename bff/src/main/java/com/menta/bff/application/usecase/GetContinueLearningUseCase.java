package com.menta.bff.application.usecase;

/**
 * Use case for the "Continuar" section of a student's dashboard (#363): the courses the student
 * is in the middle of, each with the lesson to resume.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface GetContinueLearningUseCase {

    /**
     * Reads the student's courses in progress with the caller's token.
     *
     * <p>Never fails because of the Virtual API: a missing token or any upstream failure becomes a
     * view flagged {@link ContinueLearningView#unavailable()}, so the rest of the dashboard always
     * renders. Only call it for a student; other roles have no such section.
     * </p>
     *
     * @param accessToken the student's access token, or {@code null}/blank when the request has none;
     *                    never logged
     * @return the section to render
     */
    ContinueLearningView execute(String accessToken);
}
