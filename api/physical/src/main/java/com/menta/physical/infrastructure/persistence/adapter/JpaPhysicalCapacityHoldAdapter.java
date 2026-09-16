package com.menta.physical.infrastructure.persistence.adapter;

import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.HeldSessionRow;
import com.menta.physical.application.port.out.PhysicalCapacityHoldWriter;
import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityHoldJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityHoldJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter that owns the {@code physical_capacity_holds} write path
 * (proposal — scenario 3; design B4, the proposal's High risk).
 *
 * <p>The sibling of {@link JpaPhysicalCapacityAssignmentAdapter}: same
 * invariant shape, one more locking read. A hold cannot oversell into a
 * spot an assignment already occupies, and — the risk this change exists to
 * close — an assignment cannot oversell into a spot a hold already
 * reserved. Both writers execute the SAME three statements in the SAME
 * order:</p>
 *
 * <ol>
 *   <li>{@code SELECT capacity ... FOR UPDATE} on {@code physical_sessions}
 *       — the row lock every claimant for this session queues on.</li>
 *   <li>{@code SELECT COUNT(*) ... FOR UPDATE} on
 *       {@code physical_capacity_assignments} — the assigned count, a
 *       locking read immune to this transaction's MVCC snapshot.</li>
 *   <li>{@code SELECT COUNT(*) ... FOR UPDATE} on
 *       {@code physical_capacity_holds} — the active-hold count, same
 *       locking discipline (design B4, superseding #41's D3 per this
 *       change's D3).</li>
 * </ol>
 *
 * <p>No plain (non-locking) read ever precedes step 1. The decision is
 * taken BEFORE the INSERT: {@code assigned + activeHolds + 1 > capacity}
 * refuses with {@link CapacityBelowAssignedException} and writes nothing. A
 * V7-style {@code uq_physical_holds_payment_session} collision still
 * surfaces as {@code DataIntegrityViolationException} from the explicit
 * flush, mapped by {@code CreateCapacityHoldUseCase} the same way
 * {@code AssignCapacityUseCase} maps its own UNIQUE race.</p>
 */
@Component
public class JpaPhysicalCapacityHoldAdapter implements PhysicalCapacityHoldWriter {

    private final PhysicalCapacityHoldJpaRepository holdRepository;
    private final PhysicalCapacityAssignmentJpaRepository assignmentRepository;
    private final PhysicalSessionJpaRepository sessionRepository;
    private final Clock clock;

    public JpaPhysicalCapacityHoldAdapter(
        PhysicalCapacityHoldJpaRepository holdRepository,
        PhysicalCapacityAssignmentJpaRepository assignmentRepository,
        PhysicalSessionJpaRepository sessionRepository,
        Clock clock
    ) {
        this.holdRepository = holdRepository;
        this.assignmentRepository = assignmentRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Instant assertHold(UUID sessionId, UUID paymentId, Instant expiresAt) {
        Instant now = clock.now();
        UUID rowId = UUID.randomUUID();

        // Step 1 — exclusive row lock on the session AND its capacity, in
        // one locking statement. Every claimant for this session — hold or
        // assignment — queues here, so the lock order is identical for all
        // of them.
        int capacity = sessionRepository
            .lockCapacityForUpdate(sessionId)
            .orElseThrow(SessionNotFoundException::new);

        // Step 2 — the assigned count as a LOCKING read.
        long assigned = assignmentRepository.countBySessionIdForUpdate(sessionId);

        // Step 3 — the active-hold count as a LOCKING read (design B4, the
        // proposal's High risk): without this, a non-held assignment path
        // — or a concurrent hold — could oversell into a spot this hold
        // already reserves.
        long activeHolds = holdRepository.countActiveBySessionIdForUpdate(sessionId, now);

        if (assigned + activeHolds + 1 > capacity) {
            // Invariant would be violated — refuse before writing anything.
            throw new CapacityBelowAssignedException();
        }

        holdRepository.save(new PhysicalCapacityHoldJpaEntity(
            rowId, sessionId, paymentId, expiresAt, null, now
        ));
        // Force flush so a uq_physical_holds_payment_session collision is
        // raised here, attributed to this claim, and not at commit time.
        holdRepository.flush();

        return now;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void markConverted(UUID paymentId, UUID sessionId) {
        Instant now = clock.now();

        PhysicalCapacityHoldJpaEntity existing = holdRepository.findByPaymentIdOrdered(paymentId).stream()
            .filter(row -> row.getSessionId().equals(sessionId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No hold row for paymentId=" + paymentId + " and sessionId=" + sessionId
            ));

        holdRepository.save(new PhysicalCapacityHoldJpaEntity(
            existing.getId(), existing.getSessionId(), existing.getPaymentId(),
            existing.getExpiresAt(), now, existing.getCreatedAt()
        ));
        holdRepository.flush();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void release(UUID paymentId) {
        holdRepository.deleteAll(holdRepository.findByPaymentIdOrdered(paymentId));
        holdRepository.flush();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public List<HeldSessionRow> findByPaymentIdOrdered(UUID paymentId) {
        return holdRepository.findByPaymentIdOrdered(paymentId).stream()
            .map(row -> new HeldSessionRow(row.getSessionId(), row.getConvertedAt() != null))
            .toList();
    }
}
