package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * The referenced {@link com.menta.billing.domain.model.PhysicalCourseQuote}
 * either does not exist, or its 1-hour {@code expiresAt} has already passed
 * at checkout time (design A7, #41 US-PHYSICAL-004).
 *
 * <p>Deliberately covers both cases — same anti-enumeration discipline
 * {@link PlanNotAvailableException} already applies to plans: a caller must
 * not be able to tell "unknown quoteId" apart from "this one expired".
 * Maps to {@code 410 Gone}, following this project's established precedent
 * for a time-limited artifact whose remedy is to obtain a fresh one:
 * {@code PasswordResetTokenExpiredException} maps to the same status for
 * exactly this shape. Checked <em>before</em> capacity availability
 * ({@link PhysicalCapacityUnavailableException}) — a stale quote's
 * availability reading describes a course state the caller is no longer
 * entitled to act on.</p>
 */
public class PhysicalCourseQuoteExpiredException extends BusinessException {

    private static final String ERROR_CODE = "PHYSICAL_COURSE_QUOTE_EXPIRED";

    public PhysicalCourseQuoteExpiredException() {
        super(ERROR_CODE, "Physical course quote not found or expired");
    }
}
