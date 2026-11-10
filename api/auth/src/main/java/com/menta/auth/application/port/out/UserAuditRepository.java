package com.menta.auth.application.port.out;

import com.menta.auth.domain.model.UserId;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail of administrative changes to users.
 */
public interface UserAuditRepository {

    /** Action recorded when an administrator changes a user's role. */
    String ROLE_CHANGED = "ROLE_CHANGED";

    /**
     * Records one audit entry. MUST participate in the caller's transaction so the entry commits with the change
     * it describes.
     *
     * @param userId the user the change applies to
     * @param actorId the administrator who made the change
     * @param action what happened, e.g. {@link #ROLE_CHANGED}
     * @param previousValue the value before the change, or {@code null}
     * @param newValue the value after the change, or {@code null}
     * @param occurredAt when the change happened, as decided by the application
     */
    void append(
        UserId userId, UUID actorId, String action, String previousValue, String newValue, Instant occurredAt
    );
}
