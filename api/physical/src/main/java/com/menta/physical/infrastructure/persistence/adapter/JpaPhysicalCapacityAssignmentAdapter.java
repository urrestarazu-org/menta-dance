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
 * <h2>Capacity invariant under concurrency (issue #216)</h2>
 * <p>The plain read-then-insert pattern is racy: two handlers can both
 * observe {@code assignedSpots = 0 < capacity = 1} and both insert
 * successfully, because V7 {@code UNIQUE (session_id, student_id)} only
 * blocks SAME-pair duplicates. Measured failure rate before the fix: 7 of
 * 11 runs (64%) with a real starting gun.</p>
 *
 * <p>This adapter is the single point where the invariant is decided, and
 * it decides it from THREE locking reads and nothing else (design B4 — the
 * sibling of {@link JpaPhysicalCapacityHoldAdapter}, same shape, same
 * order):</p>
 * <ol>
 *   <li>{@code SELECT capacity ... FOR UPDATE} on {@code physical_sessions}
 *       — the row lock every claimant for this session queues on, plus the
 *       capacity, in one statement.</li>
 *   <li>{@code SELECT COUNT(*) ... FOR UPDATE} on
 *       {@code physical_capacity_assignments} — the assigned count. Being a
 *       locking read it reads the latest committed rows rather than the
 *       transaction's MVCC snapshot, and its gap lock keeps a peer from
 *       inserting underneath us before we commit.</li>
 *   <li>{@code SELECT COUNT(*) ... FOR UPDATE} on
 *       {@code physical_capacity_holds} — the active-hold count, same
 *       locking discipline (design B4, the proposal's High risk): without
 *       this, an assignment could oversell into a spot an in-flight hold
 *       already reserves.</li>
 * </ol>
 *
 * <p>Neither of the first two steps may be replaced by a consistent
 * (non-locking) read. The previous implementation decided from the
 * correlated {@code COUNT(*)} subquery of
 * {@code findByIdWithAvailabilityForUpdate}: {@code FOR UPDATE} does not
 * extend to a subquery, so that count came from the snapshot. Any plain
 * read earlier in the transaction — the use case used to do exactly one,
 * before calling in here — fixes that snapshot before the row lock is ever
 * granted, and the lock then arrives too late to matter.</p>
 *
 * <p>The decision is taken BEFORE the INSERT, so there is no
 * insert-then-delete compensation any more: a claim that loses the race
 * writes nothing at all. A V7 {@code UNIQUE} collision still surfaces as
 * {@code DataIntegrityViolationException} from the explicit flush, which
 * {@code AssignCapacityUseCase} maps to
 * {@link CapacityBelowAssignedException} — the single exception type the
 * handler side recovers from.</p>
 */
@Component
public class JpaPhysicalCapacityAssignmentAdapter implements PhysicalCapacityAssignmentWriter {

    private final com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository jpaRepository;
    private final com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository sessionRepository;
    private final com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityHoldJpaRepository holdRepository;
    private final Clock clock;

    public JpaPhysicalCapacityAssignmentAdapter(
        com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository jpaRepository,
        com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository sessionRepository,
        com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityHoldJpaRepository holdRepository,
        Clock clock
    ) {
        this.jpaRepository = jpaRepository;
        this.sessionRepository = sessionRepository;
        this.holdRepository = holdRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public Instant assertAssignment(UUID sessionId, UUID studentId) {
        Instant now = clock.now();
        UUID rowId = UUID.randomUUID();

        // Step 1 — exclusive row lock on the session AND its capacity, in one
        // locking statement. Every claimant for this session queues here, so
        // the lock order is identical for all of them.
        int capacity = sessionRepository
            .lockCapacityForUpdate(sessionId)
            .orElseThrow(com.menta.physical.domain.exception.SessionNotFoundException::new);

        // Step 2 — the assigned count as a LOCKING read: immune to this
        // transaction's MVCC snapshot, and gap-locking the session's range so
        // no peer can insert before we commit.
        long assigned = jpaRepository.countBySessionIdForUpdate(sessionId);

        // Step 3 — the active-hold count as a LOCKING read (design B4, the
        // proposal's High risk): without this, an assignment could oversell
        // into a spot an in-flight hold already reserves.
        long activeHolds = holdRepository.countActiveBySessionIdForUpdate(sessionId, now);

        if (assigned + activeHolds + 1 > capacity) {
            // Invariant would be violated — refuse before writing anything.
            throw new CapacityBelowAssignedException();
        }

        jpaRepository.save(new com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity(
            rowId, sessionId, studentId, now
        ));
        // Force flush so a V7 UNIQUE (session_id, student_id) collision is
        // raised here, attributed to this claim, and not at commit time.
        jpaRepository.flush();

        return now;
    }
}
