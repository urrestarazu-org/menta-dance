package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.LibraryVideoStreamView;

/** Inbound port to preview a video of the library: a short-lived signed URL for staff (ADR-0049, #864). */
public interface GetLibraryVideoStreamUseCase {

    /**
     * Signs a playback URL for any video the library has and can play now, whether or not a lesson uses it and
     * whoever owns those lessons. Who may call it (ADMIN and INSTRUCTOR) is decided by the security layer. The
     * signed URL is never logged and never stored, and nothing is audited: it is a read.
     *
     * @throws IllegalArgumentException if the id is not a plain identifier
     * @throws com.menta.virtual.domain.exception.VideoNotFoundException if the library has no such video
     * @throws com.menta.virtual.application.exception.VideoNotPlayableException if the video cannot play yet
     * @throws com.menta.virtual.domain.exception.VideoLibraryUnavailableException if the library cannot answer
     */
    LibraryVideoStreamView get(String videoId);
}
