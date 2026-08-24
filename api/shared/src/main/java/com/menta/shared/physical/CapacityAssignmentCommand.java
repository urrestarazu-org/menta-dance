package com.menta.shared.physical;

import java.util.Objects;
import java.util.UUID;

/**
 * Cross-module record consumed by physical's {@code AssignCapacityUseCase}
 * from {@code api:app}'s outbox handler (design §4.3, proposal §4.3).
 *
 * <p>No JPA / JSON / Spring annotation: this is plain Java shared between
 * modules, never re-encoded by Hibernate or Jackson, so the consumer side
 * sees {@link java.util.UUID}-typed fields directly. Producer is the
 * {@link com.menta.app.outbox.PhysicalCapacityAssignmentOutboxEventHandler},
 * which assembles the command by mapping the outbox payload's UUID
 * (paymentId) to its stored payment row's {@code userId} (studentId) and
 * to {@code targetReference} (sessionId).</p>
 *
 * @param sessionId the physical session the buyer is paying for.
 * @param studentId the buyer's user id (Capacity row keys on this — V7 line 44 UNIQUE per (session, student)).
 * @param paymentId the billing payment id this capacity row is a consequence of. The capacity row itself does not carry this id
 *     (see V7 schema); the link is reconstructed via {@code billing_purchases.payment_id} when needed.
 */
public record CapacityAssignmentCommand(
    UUID sessionId,
    UUID studentId,
    UUID paymentId
) {

    public CapacityAssignmentCommand {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(studentId, "studentId cannot be null");
        Objects.requireNonNull(paymentId, "paymentId cannot be null");
    }
}
