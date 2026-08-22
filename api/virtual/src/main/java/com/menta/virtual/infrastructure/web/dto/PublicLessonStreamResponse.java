package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.PublicLessonStreamView;
import java.time.Instant;
import java.util.List;

/**
 * Wire shape for {@code GET /api/v1/virtual/lessons/{lessonId}/stream}
 * when the caller's entitlement was confirmed
 * (US-VIRTUAL-004 escenario 1). Mirrors the application-layer
 * {@link PublicLessonStreamView}, with the JSON rearranged into
 * {@code {stream: {...}, lesson: {...}}} so the player can treat the
 * two halves independently.
 *
 * <p>Field-block decisions:</p>
 * <ul>
 *   <li>{@code stream.url} — the signed CDN URL; the client hands it
 *       to the HLS player as-is.</li>
 *   <li>{@code stream.type} — fixed to {@code "HLS"} (constant on the
 *       orchestrator's decision).</li>
 *   <li>{@code stream.qualities} — static ladder; a future ticket may
 *       consult a Bunny.net manifest and replace this without
 *       renaming the field.</li>
 *   <li>{@code stream.expiresAt} — RFC 3339/ISO-8601 instant; format
 *       fixed by Jackson's default {@code Instant} serializer.</li>
 *   <li>{@code lesson.duration} — {@code mm:ss}; same formatter as
 *       the {@code GET /lessons/{id}} endpoint (#48).</li>
 * </ul>
 *
 * <p>The 403 access-denied branch uses a separate {@link PublicLessonAccessDto}
 * body — this record does not carry a fallback for it.</p>
 */
public record PublicLessonStreamResponse(StreamBlock stream, LessonRef lesson) {

    public record StreamBlock(
        String url,
        String type,
        List<PublicStreamQualityDto> qualities,
        Instant expiresAt
    ) {}

    public record LessonRef(
        String lessonId,
        String title,
        String duration
    ) {}

    public static PublicLessonStreamResponse from(PublicLessonStreamView view) {
        return new PublicLessonStreamResponse(
            new StreamBlock(
                view.streamUrl(),
                view.type(),
                view.qualities().stream().map(PublicStreamQualityDto::from).toList(),
                view.expiresAt()
            ),
            new LessonRef(
                view.lessonId(),
                view.lessonTitle(),
                view.lessonDurationFormatted()
            )
        );
    }
}
