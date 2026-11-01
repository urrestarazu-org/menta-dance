package com.menta.bff.infrastructure.web.validation;

import java.util.regex.Pattern;

/**
 * Phone number rules shared by the registration form and its validation.
 *
 * <p>The Auth API accepts only strict E.164 ({@code ^\+[1-9]\d{7,14}$}) and does not trim, so the BFF strips
 * the separators people naturally type before validating and sending the number.
 * </p>
 */
public final class PhoneNumbers {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\p{Z}\\-.()]");
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private PhoneNumbers() {
    }

    /**
     * Strips spaces, dashes, dots and parentheses.
     *
     * @param typed what the person typed, possibly null
     * @return the number without separators, or null when {@code typed} is null
     */
    public static String normalize(String typed) {
        return typed == null ? null : SEPARATORS.matcher(typed).replaceAll("");
    }

    /**
     * Checks a normalized number against E.164.
     *
     * @param normalized a number already passed through {@link #normalize(String)}
     * @return true when it is a plus sign followed by 8 to 15 digits, the first not zero
     */
    public static boolean isE164(String normalized) {
        return normalized != null && E164.matcher(normalized).matches();
    }
}
