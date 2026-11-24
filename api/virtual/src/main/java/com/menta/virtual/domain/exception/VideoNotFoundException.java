package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/** Thrown when the video library has no video with the requested id (#841). */
public class VideoNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "VIDEO_NOT_FOUND";

    public VideoNotFoundException() {
        super(ERROR_CODE, "The video does not exist in the library");
    }
}
