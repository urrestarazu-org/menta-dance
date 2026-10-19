package com.menta.shared.domain.vo;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object representing an email address.
 * Immutable and validates email format.
 */
public final class Email {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final String value;

    private Email(String value) {
        this.value = value;
    }

    /**
     * Creates an email from raw user input, normalizing it before validation.
     *
     * <p>Surrounding whitespace is trimmed and the address is lower-cased, so two inputs that differ
     * only in case or padding yield equal instances. The normalized form must then match a
     * deliberately simple {@code local@domain.tld} pattern; this is a format check, not a
     * deliverability check.</p>
     *
     * @param value the raw address as typed by the user
     * @return the validated, normalized email
     * @throws IllegalArgumentException if the value is null, blank or not a well-formed address
     */
    public static Email of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        String normalized = value.trim().toLowerCase();

        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid email format: " + value);
        }

        return new Email(normalized);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Email email = (Email) o;
        return Objects.equals(value, email.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
