package com.menta.bff.application.usecase;

/**
 * Use case that deletes a video of the library (#842).
 *
 * <p>Part of Clean Architecture application layer. That only an administrator can delete, and only a video that no
 * lesson uses, is the API's: it refuses with {@code VirtualVideoManagementApiClient.VideoManagementException}
 * ({@code ACCESS_DENIED} or {@code VIDEO_IN_USE}, the latter with the lessons that use it).
 * </p>
 */
public interface DeleteVideoUseCase {

    /**
     * Deletes the video.
     *
     * @param accessToken caller's access token
     * @param videoId     identifier as it came in the URL; one that is not a simple identifier is a video that
     *                    does not exist ({@code VIDEO_NOT_FOUND}, without calling the API)
     */
    void execute(String accessToken, String videoId);
}
