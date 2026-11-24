package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when an instructor acts on a video that no lesson of their own courses uses, and when a non-admin tries
 * to delete a video (#841). Ownership is derived from the courses, never from the library (ADR-0049).
 */
public class VideoNotOwnedException extends BusinessException {

    private static final String ERROR_CODE = "VIDEO_NOT_OWNED";

    public VideoNotOwnedException() {
        super(ERROR_CODE, "The video is not used by a lesson of the actor's own courses");
    }
}
