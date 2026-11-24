package com.menta.virtual.application.port.out;

import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.LibraryVideoPage;
import com.menta.virtual.application.dto.VideoLibraryQuery;
import java.util.Optional;

/**
 * Outbound port to the video library the lessons' {@code videoId} refer to (Bunny Stream, ADR-0049): reads (#840)
 * and the two writes the API exposes, rename and delete (#841); uploads are out of scope. The library key never
 * leaves the adapter. Every method throws
 * {@link com.menta.virtual.domain.exception.VideoLibraryUnavailableException} when the library cannot answer.
 */
public interface VideoLibraryPort {

    /** One page of the library's videos, filtered and ordered by the library. */
    LibraryVideoPage list(VideoLibraryQuery query);

    /** One video by id; empty when the library has no such video. */
    Optional<LibraryVideo> find(String videoId);

    /**
     * Renames a video.
     *
     * @return {@code false} when the library has no such video, {@code true} once it was renamed
     * @throws IllegalArgumentException if {@code videoId} is not a plain identifier (see the adapter)
     */
    boolean rename(String videoId, String title);

    /**
     * Deletes a video. A video the library no longer has counts as already deleted and is not an error, so the
     * call can be repeated safely. The library hides a deleted video from its queries at once but may keep
     * playing it for a while.
     *
     * @throws IllegalArgumentException if {@code videoId} is not a plain identifier (see the adapter)
     */
    void delete(String videoId);
}
