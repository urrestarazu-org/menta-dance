package com.menta.virtual.application.port.out;

import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.LibraryVideoPage;
import com.menta.virtual.application.dto.VideoLibraryQuery;
import java.util.Optional;

/**
 * Outbound port to the video library the lessons' {@code videoId} refer to (Bunny Stream, ADR-0049). Reads only;
 * the library key never leaves the adapter. Every method throws
 * {@link com.menta.virtual.domain.exception.VideoLibraryUnavailableException} when the library cannot answer.
 */
public interface VideoLibraryPort {

    /** One page of the library's videos, filtered and ordered by the library. */
    LibraryVideoPage list(VideoLibraryQuery query);

    /** One video by id; empty when the library has no such video. */
    Optional<LibraryVideo> find(String videoId);
}
