package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.LibraryVideoStreamView;
import java.time.Instant;

/**
 * Wire shape for {@code GET /api/v1/admin/virtual/videos/{videoId}/stream} (ADR-0049, #864): the {@code stream} block
 * of {@link PublicLessonStreamResponse} ({@code url}, {@code type} and {@code expiresAt}, with the same meaning and
 * the same constant {@code type}) and a {@code video} block in place of its {@code lesson} block. It leaves out
 * {@code qualities}: that is a fixed ladder of the student player, and a preview does not use it.
 *
 * <p>{@code stream.url} carries the CDN token: it is neither logged nor stored.</p>
 */
public record LibraryVideoStreamResponse(StreamBlock stream, VideoRef video) {

    public record StreamBlock(String url, String type, Instant expiresAt) {
        /** Leaves the signed URL out of any printed form. */
        @Override
        public String toString() {
            return "StreamBlock[type=" + type + ", expiresAt=" + expiresAt + "]";
        }
    }

    public record VideoRef(String videoId, String title) {}

    /** Maps the application view onto the {@code stream} and {@code video} blocks. */
    public static LibraryVideoStreamResponse from(LibraryVideoStreamView view) {
        return new LibraryVideoStreamResponse(
            new StreamBlock(view.streamUrl(), view.type(), view.expiresAt()),
            new VideoRef(view.videoId(), view.title())
        );
    }
}
