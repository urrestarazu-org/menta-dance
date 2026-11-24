package com.menta.virtual.application.dto;

import java.util.Locale;

/** Ordering the video listing supports: newest first or by title (A-Z). */
public enum LibraryVideoOrder {
    DATE,
    TITLE;

    /**
     * Parses a request value ignoring case; {@code null} and blank mean {@link #DATE}.
     *
     * @throws IllegalArgumentException for any other value
     */
    public static LibraryVideoOrder parse(String value) {
        if (value == null || value.isBlank()) {
            return DATE;
        }
        return valueOf(value.strip().toUpperCase(Locale.ROOT));
    }

    /** The value the library's {@code orderBy} parameter expects. */
    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
