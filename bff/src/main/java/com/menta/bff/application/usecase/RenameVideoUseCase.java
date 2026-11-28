package com.menta.bff.application.usecase;

/**
 * Use case that renames a video of the library (#842).
 *
 * <p>Part of Clean Architecture application layer. Which videos the caller may rename is the API's: its refusals
 * reach the caller as {@code VirtualVideoManagementApiClient.VideoManagementException}.
 * </p>
 */
public interface RenameVideoUseCase {

    /**
     * Renames the video.
     *
     * @param accessToken caller's access token
     * @param videoId     identifier as it came in the URL; one that is not a simple identifier is a video that
     *                    does not exist ({@code VIDEO_NOT_FOUND}, without calling the API)
     * @param title       the new title; blank or longer than 255 characters is an {@code INVALID_REQUEST} without
     *                    calling the API; the ends are trimmed
     */
    void execute(String accessToken, String videoId, String title);
}
