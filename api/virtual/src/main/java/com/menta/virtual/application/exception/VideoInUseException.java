package com.menta.virtual.application.exception;

import com.menta.shared.domain.exceptions.BusinessException;
import com.menta.virtual.application.dto.VideoLessonUsage;
import java.util.List;

/**
 * Thrown when deleting a video that a lesson still uses (#841). Deleting it would leave those lessons pointing at a
 * video that no longer exists, so nothing is deleted and nothing is un-assigned: the actor has to change the
 * lessons first. It carries the lessons so the client can show them without ever seeing the library's own answer.
 * Lives in {@code application} because it carries an application DTO.
 */
public class VideoInUseException extends BusinessException {

    private static final String ERROR_CODE = "VIDEO_IN_USE";

    private final transient List<VideoLessonUsage> lessons;

    public VideoInUseException(List<VideoLessonUsage> lessons) {
        super(ERROR_CODE, "A video used by a lesson cannot be deleted");
        this.lessons = List.copyOf(lessons);
    }

    /** The lessons that use the video, with their course and module, in display order. */
    public List<VideoLessonUsage> lessons() {
        return lessons;
    }
}
