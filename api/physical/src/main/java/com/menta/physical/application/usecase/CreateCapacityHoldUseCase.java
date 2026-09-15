package com.menta.physical.application.usecase;

import com.menta.physical.application.port.in.PhysicalCapacityHoldPort;
import com.menta.physical.application.port.out.PhysicalCapacityHoldWriter;
import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.shared.physical.MultiSessionCapacityHoldCommand;
import com.menta.shared.physical.SessionClaim;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims a technical, all-or-nothing hold over an ordered set of sessions
 * (proposal — scenario 3; design step 3), the sibling of
 * {@code AssignCapacityUseCase#assignAll}.
 *
 * <p>The hold invariant itself is decided one layer down, in
 * {@link PhysicalCapacityHoldWriter#assertHold}, from three locking reads
 * (design B4). This use case deliberately reads nothing about the session
 * before calling it — a plain read here would fix the transaction's MVCC
 * snapshot before the row lock is taken and reintroduce the oversell of
 * issue #216, exactly as documented on {@code AssignCapacityUseCase}.</p>
 *
 * <p>{@link #holdAll} is the one real implementation of the invariant: one
 * {@code REQUIRES_NEW} transaction wraps the whole ordered loop, so a
 * failure on any claim rolls back every earlier insert in the same set.
 * {@code release} is delegated to {@link ReleaseCapacityHoldUseCase} so
 * both entry points of {@link PhysicalCapacityHoldPort} are backed by an
 * independently unit-testable class, while the port itself has one
 * implementing bean.</p>
 */
@Component
public class CreateCapacityHoldUseCase implements PhysicalCapacityHoldPort {

    private final PhysicalCapacityHoldWriter holdWriter;
    private final ReleaseCapacityHoldUseCase releaseCapacityHoldUseCase;

    public CreateCapacityHoldUseCase(
        PhysicalCapacityHoldWriter holdWriter, ReleaseCapacityHoldUseCase releaseCapacityHoldUseCase
    ) {
        this.holdWriter = holdWriter;
        this.releaseCapacityHoldUseCase = releaseCapacityHoldUseCase;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CapacityHolds holdAll(MultiSessionCapacityHoldCommand command, Instant expiresAt) {
        for (SessionClaim claim : command.claims()) {
            claimOne(claim.sessionId(), command.paymentId(), expiresAt);
        }
        return new CapacityHolds(command.claims().stream().map(SessionClaim::sessionId).toList());
    }

    @Override
    public void release(UUID paymentId) {
        releaseCapacityHoldUseCase.release(paymentId);
    }

    /**
     * One claim of the ordered set, delegated whole to
     * {@link PhysicalCapacityHoldWriter#assertHold}. Deliberately private
     * with no {@code @Transactional} of its own — a self-invocation of an
     * annotated method on {@code this} does not go through the Spring AOP
     * proxy, so it always runs inside the {@code REQUIRES_NEW} transaction
     * already opened by {@link #holdAll}.
     */
    private void claimOne(UUID sessionId, UUID paymentId, Instant expiresAt) {
        try {
            holdWriter.assertHold(sessionId, paymentId, expiresAt);
        } catch (DataIntegrityViolationException uniqueRace) {
            // uq_physical_holds_payment_session — the second concurrent
            // INSERT lost. Route as CapacityBelowAssigned, same discipline
            // as AssignCapacityUseCase's own UNIQUE race handling.
            throw new CapacityBelowAssignedException();
        }
    }
}
