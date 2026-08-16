package com.menta.auth.domain.model;

/**
 * A single unmet password rule (US-AUTH-006).
 *
 * <p>Modelled as an enum rather than a message so the HTTP layer owns the
 * wording: the domain states <em>what</em> failed, infrastructure decides how to
 * say it and in which language.</p>
 */
public enum PasswordPolicyViolation {

    /** Fewer than {@link Password#MIN_LENGTH} characters. */
    TOO_SHORT,

    /** No uppercase letter. */
    MISSING_UPPERCASE,

    /** No digit. */
    MISSING_DIGIT
}
