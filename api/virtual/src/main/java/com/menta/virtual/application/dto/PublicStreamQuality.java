package com.menta.virtual.application.dto;

/**
 * A single rendition (quality/bitrate pair) declared by the lesson-stream
 * payload (US-VIRTUAL-004). The MVP exposes a fixed, hard-coded ladder;
 * a future issue can produce these dynamically from a Bunny.net manifest
 * without changing this record's shape.
 *
 * @param label human-readable quality label, e.g. {@code "1080p"}.
 * @param bitrate bits per second required to play this rendition. The
 *     client uses it to pick the highest ladder the connection can sustain.
 */
public record PublicStreamQuality(String label, long bitrate) {

    public static PublicStreamQuality of(String label, long bitrate) {
        return new PublicStreamQuality(label, bitrate);
    }
}
