package com.menta.bff.application.usecase;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Shape check for identifiers that end up in a path segment or a request body: only a canonical UUID is ever
 * sent upstream, whatever the browser submitted.
 */
final class UuidFormat {

    private static final Pattern UUID_FORMAT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private UuidFormat() {
    }

    /**
     * Strips and lower-cases a UUID.
     *
     * @param value identifier as submitted, may be {@code null}
     * @return the normalized identifier, or {@code null} when it is not a UUID
     */
    static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String id = value.strip().toLowerCase(Locale.ROOT);
        return UUID_FORMAT.matcher(id).matches() ? id : null;
    }
}
