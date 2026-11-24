package com.menta.virtual.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when the video library (Bunny Stream) cannot answer: it is unreachable, times out, answers with an error
 * or with a body that cannot be read, or rejects the library key (#840). The message only describes the kind of
 * failure; it never carries the library's response body or any credential.
 */
public class VideoLibraryUnavailableException extends BusinessException {

    private static final String ERROR_CODE = "VIDEO_LIBRARY_UNAVAILABLE";

    public VideoLibraryUnavailableException(String message) {
        super(ERROR_CODE, message);
    }

    public VideoLibraryUnavailableException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
