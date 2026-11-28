package com.menta.bff.application.usecase;

/**
 * Use case that lists the videos of the library the caller manages (#842): the whole library for an administrator,
 * the videos of the own courses' lessons for an instructor, as the API decides.
 *
 * <p>Part of Clean Architecture application layer. A failure of the API, of the library or a missing token reads as
 * {@link VideosListView#unavailableView(String, boolean)}; the one refusal that is not an outage,
 * {@code ACCESS_DENIED}, reaches the caller as {@code VirtualVideoManagementApiClient.VideoManagementException}.
 * </p>
 */
public interface ListManagedVideosUseCase {

    /**
     * Lists one page of videos.
     *
     * @param accessToken caller's access token
     * @param search      text the title must contain, as it came in the query string; blank is no filter and a long
     *                    text is cut to what the API accepts
     * @param page        zero-based page index; {@code null} or negative is the first and one past the end shows the
     *                    last
     * @return the page worded for the screen, possibly without rows
     */
    VideosListView execute(String accessToken, String search, Integer page);
}
