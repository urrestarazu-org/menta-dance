package com.menta.shared.auth;

import java.util.Optional;
import java.util.UUID;

/**
 * Read contract through which Billing resolves a buyer's email address to deliver a decision
 * notification (#33, US-BILLING-005, design C8).
 *
 * <p>{@link UserExistencePort} deliberately never returns an email — its own javadoc states "a
 * projection would leak identity data into billing" — so this is a new, narrow sibling, consumed
 * <strong>only</strong> by billing's infrastructure mail adapter. Billing's application layer
 * still passes a bare {@code UUID}; the resolved address never crosses into {@code application}
 * or {@code domain} (ADR-0021), preserving the spirit of {@code UserExistencePort}'s rule while
 * making the buyer email deliverable.</p>
 */
public interface UserContactPort {

    /**
     * Resolves the address at which a user can currently be reached by email.
     *
     * <p>The lookup is read-only and never fails for an unknown user: absence is expressed as an
     * empty result so the caller can decide whether to skip the notification.</p>
     *
     * @param userId the user whose contact address is requested
     * @return the user's current email, or {@link Optional#empty()} when no such user exists
     */
    Optional<String> emailOf(UUID userId);
}
