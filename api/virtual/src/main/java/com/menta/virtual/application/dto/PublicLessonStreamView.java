package com.menta.virtual.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * Application-layer view of a lesson stream (US-VIRTUAL-004): the signed
 * CDN URL, its delivery shape, its expiry, and the minimal lesson
 * metadata the player chrome needs to render the lesson title and
 * duration. The controller maps each field 1:1 onto
 * {@link com.menta.virtual.infrastructure.web.dto.PublicLessonStreamResponse}.
 *
 * <p>{@code type} is fixed to {@code "HLS"} by orchestrator decision —
 * this MVP does not branch on the underlying CDN format. A future ticket
 * can lift this into a per-lesson field without changing the surrounding
 * sealed result.</p>
 *
 * <p>{@code qualities} is a static ladder in this PR
 * ({@code 1080p/720p/480p/360p}); a future ticket can replace the static
 * allocation with a real Bunny.net manifest lookup without renaming
 * this field.</p>
 *
 * @param streamUrl signed CDN URL the player will load.
 * @param type delivery protocol, fixed to {@code "HLS"} by the MVP.
 * @param qualities adaptive-quality ladder offered by the signed stream.
 * @param expiresAt epoch-aligned instant at which the stream URL stops serving.
 * @param lessonId opaque lesson id, echoed so the player can correlate the
 *     stream with metadata it cached from {@code GET /lessons/{id}}.
 * @param lessonTitle human-readable title for the player's chrome.
 * @param lessonDurationFormatted {@code mm:ss} — same formatter contract as
 *     {@link com.menta.virtual.application.usecase.GetPublicLessonUseCaseImpl}.
 *     See the orchestrator note in
 *     {@link com.menta.virtual.application.usecase.GetPublicLessonStreamUseCaseImpl}
 *     on why the formatter is duplicated instead of shared.
 */
public record PublicLessonStreamView(
    String streamUrl,
    String type,
    List<PublicStreamQuality> qualities,
    Instant expiresAt,
    String lessonId,
    String lessonTitle,
    String lessonDurationFormatted
) {
}
