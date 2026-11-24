package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.ManagedVideo;
import java.util.UUID;

/** Inbound port to rename a video of the library (ADR-0049, #841). */
public interface RenameLibraryVideoUseCase {

    /** The longest title accepted; the lesson, module and course titles are {@code VARCHAR(255)} too. */
    int MAX_TITLE_LENGTH = 255;

    /**
     * Renames the video and audits it. An admin may rename any video of the library; anyone else only a video used
     * by a lesson of their own courses (ownership comes from the courses, never from the library).
     *
     * @return the video with its new title and the lessons that use it, as far as the actor may see them
     * @throws IllegalArgumentException if the id is not a plain identifier or the title is blank or too long
     * @throws com.menta.virtual.domain.exception.VideoNotOwnedException if a non-admin does not own the video
     * @throws com.menta.virtual.domain.exception.VideoNotFoundException if the library has no such video
     * @throws com.menta.virtual.domain.exception.VideoLibraryUnavailableException if the library cannot answer
     */
    ManagedVideo rename(String videoId, String title, UUID actingUserId, boolean actingAsAdmin);
}
