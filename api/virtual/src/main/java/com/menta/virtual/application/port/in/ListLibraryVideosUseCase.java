package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.ManagedVideoPage;
import com.menta.virtual.application.dto.VideoLibraryQuery;
import java.util.UUID;

/** Inbound port for the video management listing (ADR-0049, #840). */
public interface ListLibraryVideosUseCase {

    int DEFAULT_PAGE_SIZE = 20;
    int MAX_PAGE_SIZE = 100;

    /**
     * Lists the whole library, flagging as missing the video ids that lessons use and the library no longer has.
     * What differs by role is the lessons attached to each video: an admin gets those of any course, anyone else
     * only those of the actor's own courses (a video used only by someone else's lessons has none). Ownership
     * comes from the courses, never from the library.
     *
     * @throws IllegalArgumentException if the page size exceeds {@value #MAX_PAGE_SIZE}
     * @throws com.menta.virtual.domain.exception.VideoLibraryUnavailableException if the library cannot answer
     */
    ManagedVideoPage list(VideoLibraryQuery query, UUID actingUserId, boolean actingAsAdmin);
}
