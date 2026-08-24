package com.menta.physical.infrastructure.persistence.adapter;

import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentWriter;
import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter that owns the {@code physical_capacity_assignments} write
 * path (proposal §4; design §5.3).
 *
 * <p>Implements only the NEW write-side port
 * {@link PhysicalCapacityAssignmentWriter} — never the existing read-only
 * {@code PhysicalCapacityAssignmentRepository}, which keeps the canonical
 * read adapter ({@code PhysicalCapacityAssignmentRepositoryAdapter}) the
 * single producer of the {@code required=true} bean for that interface.
 * The TASK-005 plan explicitly forbids putting a new write method on the
 * existing read port; this adapter strictly honours that contract.</p>
 *
 * <h2>Capacity invariant after commit</h2>
 * <p>The plain read-then-insert pattern is racy under high concurrency:
 * two handlers can both observe {@code assignedSpots = 0 < capacity = 1}
 * (the SELECT reads before the peer's INSERT commits) and both insert
 * successfully because V7 {@code UNIQUE (session_id, student_id)} only
 * blocks SAME-pair duplicates. After the local INSERT commits, this
 * adapter re-reads the live count of rows for the session and ROLLBACKS
 * the insert via {@link com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository#deleteBySessionIdAndStudentId}
 * if the count exceeds capacity, then throws
 * {@link CapacityBelowAssignedException}. The same exception type the
 * use case's read-time check throws — single point of recovery on the
 * handler side.</p>
 */
@Component
public class JpaPhysicalCapacityAssignmentAdapter implements PhysicalCapacityAssignmentWriter {

    private final com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository jpaRepository;
    private final com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository sessionRepository;
    private final Clock clock;

    public JpaPhysicalCapacityAssignmentAdapter(
        com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository jpaRepository,
        com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository sessionRepository,
        Clock clock
    ) {
        this.jpaRepository = jpaRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Instant assertAssignment(UUID sessionId, UUID studentId) {
        Instant now = clock.now();
        UUID rowId = UUID.randomUUID();

        // Acquire exclusive row lock through SELECT ... FOR UPDATE — concurrent
        // adapters serialize here. After the lock is granted, the row's
        // committed capacity-driven count reflects any peer's already-committed
        // INSERT (because the peer's row would have committed BEFORE releasing
        // the lock and we always wait on the lock until they commit).
        var projection = sessionRepository
            .findByIdWithAvailabilityForUpdate(sessionId, now)
            .orElseThrow(com.menta.physical.domain.exception.SessionNotFoundException::new);

        jpaRepository.save(new com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity(
            rowId, sessionId, studentId, now
        ));
        // Force flush so the correlated subquery on the next read sees our row.
        jpaRepository.flush();

        if (projection.getAssignedSpots() + 1 > projection.getCapacity()) {
            // Invariant violated — race lost. Roll back our row before throwing.
            jpaRepository.deleteById(rowId);
            throw new CapacityBelowAssignedException();
        }
        return now;
    }
}
