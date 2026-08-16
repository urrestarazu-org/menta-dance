package com.menta.auth.domain.model;

import com.menta.auth.domain.exception.WeakPasswordException;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * A plaintext password that has been validated against the account policy
 * (US-AUTH-006): at least {@value #MIN_LENGTH} characters, one uppercase letter
 * and one digit.
 *
 * <p>The policy lives here and only here. Registration and password reset both
 * construct through {@link #of(String)}, so it is not possible to introduce a
 * credential that satisfies one flow and not the other — the mismatch that let
 * a user register a password they could never reset to.</p>
 *
 * <p>This type holds the secret in the clear and exists only long enough to be
 * handed to the encoder. It deliberately overrides {@code toString} so it cannot
 * leak through log lines, exception messages or debugger output, which is where
 * plaintext credentials usually escape from.</p>
 */
public final class Password {

    /** Minimum length required by the policy. */
    public static final int MIN_LENGTH = 8;

    private final String value;

    private Password(String value) {
        this.value = value;
    }

    /**
     * @throws WeakPasswordException carrying <em>every</em> unmet rule, so the
     *     caller can report them all at once.
     */
    public static Password of(String raw) {
        Set<PasswordPolicyViolation> violations = violationsOf(raw);
        if (!violations.isEmpty()) {
            throw new WeakPasswordException(violations);
        }
        return new Password(raw);
    }

    private static Set<PasswordPolicyViolation> violationsOf(String raw) {
        Set<PasswordPolicyViolation> violations = EnumSet.noneOf(PasswordPolicyViolation.class);
        if (raw == null || raw.isBlank()) {
            // A blank value fails every rule; reporting all three is more
            // useful than a separate "empty" outcome.
            return EnumSet.allOf(PasswordPolicyViolation.class);
        }
        // Length is counted on the raw value, never trimmed: a leading or
        // trailing space is a legitimate character, and silently dropping it
        // would let someone set a secret they can never type back identically.
        if (raw.length() < MIN_LENGTH) {
            violations.add(PasswordPolicyViolation.TOO_SHORT);
        }
        if (raw.chars().noneMatch(Character::isUpperCase)) {
            violations.add(PasswordPolicyViolation.MISSING_UPPERCASE);
        }
        if (raw.chars().noneMatch(Character::isDigit)) {
            violations.add(PasswordPolicyViolation.MISSING_DIGIT);
        }
        return violations;
    }

    /** @return the plaintext, for handing to the password encoder only. */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Password password)) {
            return false;
        }
        return value.equals(password.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    /** Never renders the secret. */
    @Override
    public String toString() {
        return "Password[PROTECTED]";
    }
}
