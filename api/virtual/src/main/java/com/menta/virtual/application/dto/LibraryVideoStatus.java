package com.menta.virtual.application.dto;

/** Processing state of a video in the library, as the library reports it ({@code UNKNOWN} for a code we do not map). */
public enum LibraryVideoStatus {
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
