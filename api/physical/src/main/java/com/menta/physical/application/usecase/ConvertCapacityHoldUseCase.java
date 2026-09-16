package com.menta.physical.application.usecase;

import com.menta.physical.application.port.out.HeldSessionRow;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentWriter;
import com.menta.physical.application.port.out.PhysicalCapacityHoldWriter;
import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Converts an existing technical hold into real
 * {@code physical_capacity_assignments} rows (proposal — scenario 6;
 * design B3), the structural twin of {@code AssignCapacityUseCase#assignAll}
 * for the confirmation-time write path.
 *
 * <p>One {@code REQUIRES_NEW} transaction wraps the whole ordered loop, in
 * the hold's own claim order ({@code (scheduledAt ASC, sessionId ASC)},
 * read via {@link PhysicalCapacityHoldWriter#findByPaymentIdOrdered}). Per
 * session:</p>
 *
 * <ol>
 *   <li>{@link PhysicalCapacityHoldWriter#markConverted} — excludes this row
 *       from the locking hold count the very next step reads, so the
 *       invariant arithmetic balances exactly (design B3's key insight).</li>
 *   <li>{@link PhysicalCapacityAssignmentWriter#assertAssignment} —
 *       unchanged, the same writer {@code AssignCapacityUseCase} calls.</li>
 * </ol>
 *
 * <p>Conversion therefore needs zero new invariant logic: it is entirely
 * composed of the two writers Phases 3 and 4 already proved under
 * concurrency.</p>
 */
@Component
public class ConvertCapacityHoldUseCase {

    private final PhysicalCapacityHoldWriter holdWriter;
    private final PhysicalCapacityAssignmentWriter assignmentWriter;

    public ConvertCapacityHoldUseCase(
        PhysicalCapacityHoldWriter holdWriter, PhysicalCapacityAssignmentWriter assignmentWriter
    ) {
        this.holdWriter = holdWriter;
        this.assignmentWriter = assignmentWriter;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConvertOutcome convertAll(UUID paymentId, UUID studentId) {
        List<HeldSessionRow> rows = holdWriter.findByPaymentIdOrdered(paymentId);

        if (rows.isEmpty()) {
            return ConvertOutcome.HoldNotFound.INSTANCE;
        }
        if (rows.stream().allMatch(HeldSessionRow::converted)) {
            return ConvertOutcome.AlreadyConverted.INSTANCE;
        }

        List<UUID> sessionIds = rows.stream().map(HeldSessionRow::sessionId).toList();
        for (UUID sessionId : sessionIds) {
            convertOne(paymentId, sessionId, studentId);
        }
        return new ConvertOutcome.Converted(sessionIds);
    }

    /**
     * One session of the ordered set. Deliberately private with no
     * {@code @Transactional} of its own — a self-invocation of an annotated
     * method on {@code this} does not go through the Spring AOP proxy, so it
     * always runs inside the {@code REQUIRES_NEW} transaction already opened
     * by {@link #convertAll}.
     */
    private void convertOne(UUID paymentId, UUID sessionId, UUID studentId) {
        holdWriter.markConverted(paymentId, sessionId);
        try {
            assignmentWriter.assertAssignment(sessionId, studentId);
        } catch (DataIntegrityViolationException uniqueRace) {
            // V7 UNIQUE (session_id, student_id) — same discipline as
            // AssignCapacityUseCase#claimOne: route as CapacityBelowAssigned
            // so api:app funnels it into the EXCEPTION residual. The
            // surrounding REQUIRES_NEW transaction rolls back the
            // markConverted call too — all-or-nothing.
            throw new CapacityBelowAssignedException();
        }
    }
}
