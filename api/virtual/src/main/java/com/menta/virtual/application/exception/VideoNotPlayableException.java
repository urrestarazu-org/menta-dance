package com.menta.virtual.application.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown when the library has the video but it cannot be played now (#864): it is still being processed, has no file
 * yet, failed, has no MP4 copy (the CDN serves the MP4, ADR-0049 decision 8) or its state is not known. The
 * {@link Reason} lets the HTTP layer say why in plain words; nothing of the library's own answer is ever carried.
 */
public class VideoNotPlayableException extends BusinessException {

    private static final String ERROR_CODE = "VIDEO_NOT_PLAYABLE";

    /** Why the video does not play. */
    public enum Reason {
        /** The library is still encoding it. */
        PROCESSING,
        /** The video was created but its file has not arrived. */
        WAITING_FOR_FILE,
        /** The upload or the processing failed. */
        FAILED,
        /** It is ready but has no MP4 copy, which is what the CDN serves. */
        NO_MP4_COPY,
        /** The library reports a state this API does not know. */
        UNKNOWN_STATE
    }

    private final Reason reason;

    public VideoNotPlayableException(Reason reason) {
        super(ERROR_CODE, "The video cannot be played now: " + reason);
        this.reason = reason;
    }

    /** Why the video does not play. */
    public Reason reason() {
        return reason;
    }
}
