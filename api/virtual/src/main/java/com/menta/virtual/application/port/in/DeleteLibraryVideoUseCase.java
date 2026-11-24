package com.menta.virtual.application.port.in;

import java.util.UUID;

/** Inbound port to delete a video of the library (ADR-0049, #841). */
public interface DeleteLibraryVideoUseCase {

    /**
     * Deletes the video from the library and audits it. Only an admin may delete, and only a video no lesson of any
     * course uses; deleting a video the library no longer has is a success. Never un-assigns a video from a lesson.
     *
     * @throws IllegalArgumentException if the id is not a plain identifier
     * @throws com.menta.virtual.domain.exception.VideoNotOwnedException if the actor is not an admin
     * @throws com.menta.virtual.application.exception.VideoInUseException if a lesson uses the video; the library
     *     is not contacted and nothing is audited
     * @throws com.menta.virtual.domain.exception.VideoLibraryUnavailableException if the library cannot answer
     */
    void delete(String videoId, UUID actingUserId, boolean actingAsAdmin);
}
