package com.menta.auth.domain.model;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Personal data a student provides when registering.
 *
 * <p>The names are trimmed and required, the phone must already be normalized to E.164 (callers strip spaces,
 * dashes and parentheses before it gets here) and the birth date is optional but, when present, must be in the
 * past. This is personal data: {@link #toString()} never prints it.
 *
 * @param firstName given name, trimmed, 1 to {@value #NAME_MAX_LENGTH} characters
 * @param lastName family name, trimmed, 1 to {@value #NAME_MAX_LENGTH} characters
 * @param phone phone number in E.164 format
 * @param birthDate date of birth, or {@code null} when not provided
 */
public record UserProfile(String firstName, String lastName, String phone, LocalDate birthDate) {

    /** Maximum length of the first and last name. */
    public static final int NAME_MAX_LENGTH = 100;

    /** E.164: a plus sign, a first digit 1-9 and 7 to 14 more digits (8 to 15 digits in total). */
    public static final String PHONE_REGEX = "^\\+[1-9]\\d{7,14}$";

    private static final Pattern PHONE_PATTERN = Pattern.compile(PHONE_REGEX);

    /**
     * Validates and normalizes the profile.
     *
     * @throws IllegalArgumentException when a name is blank or too long, the phone is not E.164 or the birth date
     *     is not in the past
     */
    public UserProfile {
        firstName = requireName(firstName, "First name");
        lastName = requireName(lastName, "Last name");
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new IllegalArgumentException("Phone must be in E.164 format");
        }
        if (birthDate != null && !birthDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Birth date must be in the past");
        }
    }

    private static String requireName(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        String trimmed = value.strip();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(label + " must not exceed " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    /** Never prints the personal data held by this record. */
    @Override
    public String toString() {
        return "UserProfile{firstName=***, lastName=***, phone=***, birthDate=***}";
    }
}
