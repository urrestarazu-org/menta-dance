package com.menta.shared.auth;

import java.util.Optional;
import java.util.UUID;

/**
 * Read contract through which Billing resolves the buyer's email to notify on
 * a {@code Purchase} EXCEPTION (proposal D3/D7, design C4).
 *
 * <p>Deliberately the smallest contract that answers the question: an
 * {@code Optional<String>} email. It never returns a {@code User}, a name, a
 * role, or a status. A projection would leak identity data into {@code
 * billing} and invite reuse creep — same minimal-contract argument as {@link
 * UserExistencePort} (design.md A15-equivalent for this port).</p>
 */
public interface UserEmailLookupPort {

    /**
     * Email only: never a {@code User}, a name, a role, or a status.
     *
     * @param userId the candidate user identifier
     * @return the resolved email, or {@code Optional.empty()} if no user with this id exists
     */
    Optional<String> findEmailById(UUID userId);
}
