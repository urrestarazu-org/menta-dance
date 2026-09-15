package com.menta.physical.application.usecase;

import com.menta.physical.application.port.in.PhysicalCapacityAssignmentPort;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentWriter;
import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.shared.physical.CapacityAssignmentCommand;
import com.menta.shared.physical.MultiSessionCapacityAssignmentCommand;
import com.menta.shared.physical.SessionClaim;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims capacity for an ordered set of sessions, or refuses with
 * {@link CapacityBelowAssignedException}.
 *
 * <p>The capacity invariant itself is decided one layer down, in
 * {@link PhysicalCapacityAssignmentWriter#assertAssignment}, from two
 * locking reads (no cached counter — see {@code PhysicalSession}'s
 * Javadoc). This use case deliberately reads nothing about the session
 * before calling it: a plain read here fixes the transaction's MVCC
 * snapshot before the row lock is taken and reintroduces the oversell of
 * issue #216. See {@link #claimOne}.</p>
 *
 * <ul>
 *   <li>Capacity trip — the writer refuses and throws
 *       {@link CapacityBelowAssignedException}; nothing is inserted.</li>
 *   <li>UNIQUE race trip — V7 {@code UNIQUE (session_id, student_id)}
 *       collision: caught and rethrown as
 *       {@link CapacityBelowAssignedException} so {@code api:app} can route
 *       to {@code EXCEPTION} via the same catch it already has.</li>
 *   <li>Missing session — the writer's locking capacity read finds no row
 *       and throws {@link SessionNotFoundException}, which propagates
 *       unchanged.</li>
 * </ul>
 *
 * <p><b>Single vs. multi-session (design A3).</b> {@link #assignAll} is the
 * one real implementation of the capacity invariant: it claims every
 * session in {@code command.claims()}, in order, under a single
 * {@code REQUIRES_NEW} transaction wrapped around the whole loop, so a
 * failure on any claim rolls back every earlier insert in the same set —
 * N independent {@code REQUIRES_NEW} calls would each be their own
 * transaction, which is exactly the partial-assignment hazard the
 * all-or-nothing NFR forbids. {@link #assign} is kept for existing
 * single-session callers and is a pure delegation to {@link #assignAll}
 * with a singleton claim list, so the N=1 path cannot drift from the N&gt;1
 * one. It carries its own {@code REQUIRES_NEW} because a self-invocation
 * of {@code assignAll} from within this class does not go through the
 * Spring proxy — the transaction boundary must already be open before the
 * delegating call, exactly like {@link #claimOne} below.</p>
 */
@Component
public class AssignCapacityUseCase implements PhysicalCapacityAssignmentPort {

    private final PhysicalCapacityAssignmentWriter assignmentWriter;

    public AssignCapacityUseCase(PhysicalCapacityAssignmentWriter assignmentWriter) {
        this.assignmentWriter = assignmentWriter;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AssignmentOutcome assign(CapacityAssignmentCommand command) {
        // Singleton delegation to assignAll (design A3): the compact
        // constructor's total-order check is vacuous for a one-element
        // list, so any scheduledAt value satisfies it — EPOCH is a plain
        // placeholder, never compared against anything.
        SessionClaim onlyClaim = new SessionClaim(command.sessionId(), Instant.EPOCH);
        MultiSessionCapacityAssignmentCommand singleton = new MultiSessionCapacityAssignmentCommand(
            List.of(onlyClaim), command.studentId(), command.paymentId()
        );

        assignAll(singleton);
        return AssignmentOutcome.ASSIGNED.INSTANCE;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CapacityAssignments assignAll(MultiSessionCapacityAssignmentCommand command) {
        for (SessionClaim claim : command.claims()) {
            claimOne(claim.sessionId(), command.studentId());
        }
        return new CapacityAssignments(command.claims().stream().map(SessionClaim::sessionId).toList());
    }

    /**
     * One claim of the ordered set, delegated whole to
     * {@link PhysicalCapacityAssignmentWriter#assertAssignment}: it decides
     * the capacity invariant under two locking reads, inserts, and flushes
     * immediately so a unique-index violation is detected and attributed to
     * this exact claim, not to the transaction as a whole — same discipline
     * as {@code SubscriptionRepositoryAdapter.saveNewCheckout}.
     *
     * <p><b>No early read here (issue #216).</b> This method used to load
     * the session through {@code findByIdWithAvailability} and fail fast on
     * {@code assignedSpots >= capacity}. That read was a non-locking
     * consistent read, and under REPEATABLE READ it fixed the transaction's
     * MVCC snapshot BEFORE the writer's {@code FOR UPDATE} ran — so the
     * count the lock was supposed to protect came back frozen from before
     * the peer's commit, and two claims could oversell a capacity-1
     * session. Measured: 7 failures in 11 runs with the early read, 0 in 5
     * without it. The writer is now the single authority on the invariant;
     * re-introducing any plain read of the session before it would
     * re-introduce the defect.</p>
     *
     * <p>The missing-session case is not lost with that read: {@code
     * assertAssignment} raises {@link SessionNotFoundException} itself when
     * its locking capacity read finds no row.</p>
     *
     * <p>Deliberately private with no {@code @Transactional} of its own: a
     * self-invocation of an annotated method on {@code this} does not go
     * through the Spring AOP proxy, so annotating it would only mislead.
     * It always runs inside the {@code REQUIRES_NEW} transaction already
     * opened by {@link #assign} or {@link #assignAll}.</p>
     */
    private void claimOne(UUID sessionId, UUID studentId) {
        try {
            assignmentWriter.assertAssignment(sessionId, studentId);
        } catch (DataIntegrityViolationException uniqueRace) {
            // V7 UNIQUE (session_id, student_id) — the second concurrent
            // INSERT lost. Per design §5.3, route as CapacityBelowAssigned
            // so api:app funnels it into the EXCEPTION residual.
            throw new CapacityBelowAssignedException();
        }
    }
}
