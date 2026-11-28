package com.menta.bff.application.usecase;

/**
 * Use case that lists the videos the lesson form offers to pick (#843): the first page of the library, as big as the
 * API allows, in the API's order. The API decides which videos each role sees; since #861 both the administrator and
 * the instructor get the whole library.
 *
 * <p>Part of Clean Architecture application layer. It never throws for a failure of the API: whatever goes wrong
 * (a refusal, an outage, a missing token) reads as {@link VideoChoices#unavailableChoices()}, because the form must
 * still render and save without the list.
 * </p>
 */
public interface ListVideoChoicesUseCase {

    /**
     * Lists the choices.
     *
     * @param accessToken     caller's access token
     * @param selectedVideoId the lesson's current video, or the one just submitted; blank for none. The API has no
     *                        read of a single video, so when the page does not have it, it is added as an option
     *                        that cannot say whether it plays
     * @return the choices, possibly unavailable
     */
    VideoChoices execute(String accessToken, String selectedVideoId);
}
