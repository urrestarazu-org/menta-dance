package com.menta.billing.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The audited decision that closed an {@code EXCEPTION} purchase: what was decided, why, by whom
 * and when. The note is stripped and must be non-blank and at most {@value #MAX_NOTE_LENGTH}
 * characters.
 */
public record PurchaseResolution(PurchaseResolutionType type, String note, UUID resolvedBy, Instant resolvedAt) {

    public static final int MAX_NOTE_LENGTH = 500;

    public PurchaseResolution {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(note, "note cannot be null");
        Objects.requireNonNull(resolvedBy, "resolvedBy cannot be null");
        Objects.requireNonNull(resolvedAt, "resolvedAt cannot be null");
        note = note.strip();
        if (note.isEmpty()) {
            throw new IllegalArgumentException("note cannot be blank");
        }
        if (note.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("note cannot exceed " + MAX_NOTE_LENGTH + " characters");
        }
    }
}
