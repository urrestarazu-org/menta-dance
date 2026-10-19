package com.menta.bff.application.dto;

import java.time.Instant;

/**
 * Signed stream descriptor, trimmed from
 * {@code PublicLessonStreamResponse.StreamBlock} to what the player needs.
 * The fixed {@code type: "HLS"} and the quality ladder are dropped —
 * neither varies today, so there is nothing for the BFF to branch on.
 *
 * @param url       signed CDN URL the player consumes as-is
 * @param expiresAt when the signed URL stops being valid
 */
public record LessonStream(String url, Instant expiresAt) {
}
