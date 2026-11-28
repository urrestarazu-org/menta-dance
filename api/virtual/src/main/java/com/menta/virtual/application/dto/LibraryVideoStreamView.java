package com.menta.virtual.application.dto;

import java.time.Instant;

/**
 * A signed playback URL for one video of the library.
 *
 * @param videoId the library's identifier of the video
 * @param title the video's title, as the library reports it
 * @param streamUrl the signed CDN URL; it carries a token, so it must never be logged
 * @param type delivery protocol, the same constant the public lesson stream reports
 * @param expiresAt instant at which the URL stops serving
 */
public record LibraryVideoStreamView(String videoId, String title, String streamUrl, String type, Instant expiresAt) {

    /** Leaves the signed URL out, so a log line or an assertion message that prints the view never leaks the token. */
    @Override
    public String toString() {
        return "LibraryVideoStreamView[videoId=" + videoId + ", expiresAt=" + expiresAt + "]";
    }
}
