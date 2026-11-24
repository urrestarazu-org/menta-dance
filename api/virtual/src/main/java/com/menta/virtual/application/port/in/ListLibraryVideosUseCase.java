package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.ManagedVideoPage;
import com.menta.virtual.application.dto.VideoLibraryQuery;
import java.util.UUID;

/** Inbound port for the video management listing (ADR-0049, #840). */
public interface ListLibraryVideosUseCase {

    int DEFAULT_PAGE_SIZE = 20;
    int MAX_PAGE_SIZE = 100;

    /**
     * Lists the videos for an admin, and only the videos used by lessons of the actor's own courses otherwise.
     * The admin gets everything: the whole library plus the video ids that lessons of any course use and the
     * library no longer has, flagged as missing. Ownership comes from the courses, never from the library.
     *
     * @throws IllegalArgumentException if the page size exceeds {@value #MAX_PAGE_SIZE}
     * @throws com.menta.virtual.domain.exception.VideoLibraryUnavailableException if the library cannot answer
     */
    ManagedVideoPage list(VideoLibraryQuery query, UUID actingUserId, boolean actingAsAdmin);
}
