package com.menta.virtual.application.dto;

import java.util.List;

/**
 * One row of the management listing: a video id, what the library knows about it and the lessons that use it.
 *
 * @param video the library's data, {@code null} when a lesson points at a video the library no longer has
 * @param lessons the lessons that use the video, as far as the actor may see them
 */
public record ManagedVideo(String videoId, LibraryVideo video, List<VideoLessonUsage> lessons) {

    public ManagedVideo {
        lessons = List.copyOf(lessons);
    }

    /** Whether a lesson points at this id but the library has no such video. */
    public boolean missingInLibrary() {
        return video == null;
    }
}
