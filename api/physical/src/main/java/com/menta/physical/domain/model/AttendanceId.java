package com.menta.physical.domain.model;

/**
 * Marker type for an {@link Attendance}'s primary key — currently a thin
 * wrapper over {@link java.util.UUID}. Exists primarily so the domain code
 * never has to talk {@code UUID} directly and the persistence layer can swap
 * the encoding (e.g. a future {@code BINARY(16)} vs {@code CHAR(36)} decision)
 * without rippling into the use cases.
 */
public record AttendanceId(java.util.UUID value) {

    public AttendanceId {
        if (value == null) {
            throw new IllegalArgumentException("AttendanceId cannot be null");
        }
    }

    public static AttendanceId generate() {
        return new AttendanceId(java.util.UUID.randomUUID());
    }

    public static AttendanceId of(java.util.UUID value) {
        return new AttendanceId(value);
    }

    public static AttendanceId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AttendanceId cannot be null or empty");
        }
        try {
            return new AttendanceId(java.util.UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid AttendanceId format: " + value, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
