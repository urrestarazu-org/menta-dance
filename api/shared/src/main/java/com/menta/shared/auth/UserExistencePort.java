package com.menta.shared.auth;

import java.util.UUID;

/**
 * Read contract through which Billing confirms a target user exists before granting a trial
 * subscription (US-BILLING-012, design.md A8/D8).
 *
 * <p>Deliberately the smallest contract that answers the question: a single boolean. It never
 * returns a {@code User}, an email, a role, or a status. A projection would leak identity data
 * into {@code billing} and invite reuse creep; this port exists only to reject an unknown
 * {@code userId} before anything is written (design.md A15).</p>
 *
 * <p>{@code shared/billing/} (ADR-0039) is the shape precedent for a bidirectional {@code shared}
 * port; this is the first one in the opposite direction, {@code billing → shared ← auth}.</p>
 */
public interface UserExistencePort {

    /**
     * Existence only: never a {@code User}, an email, a role, or a status.
     *
     * @param userId the candidate user identifier
     * @return {@code true} only if a user with this id currently exists
     */
    boolean existsById(UUID userId);
}
