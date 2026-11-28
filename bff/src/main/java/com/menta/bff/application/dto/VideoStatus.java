package com.menta.bff.application.dto;

/**
 * Processing state of a video in the library ({@code VirtualVideoResponse.status}). {@code UNKNOWN} stands for a
 * value this BFF does not know yet, and for a video that no longer exists in the library (it has no state).
 */
public enum VideoStatus {
    CREATED,
    UPLOADED,
    PROCESSING,
    TRANSCODING,
    FINISHED,
    ERROR,
    UPLOAD_FAILED,
    JIT_SEGMENTING,
    JIT_PLAYLISTS_CREATED,
    UNKNOWN
}
