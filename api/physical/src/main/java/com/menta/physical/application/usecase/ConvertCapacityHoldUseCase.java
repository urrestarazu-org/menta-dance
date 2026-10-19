package com.menta.physical.application.usecase;

import com.menta.physical.application.port.out.HeldSessionRow;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentWriter;
import com.menta.physical.application.port.out.PhysicalCapacityHoldWriter;
import com.menta.shared.physical.AssignmentType;
import java.util.List;
import java.util.UUID;
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

    /**
     * Converts every hold of a payment into confirmed assignments, in the hold's own claim order.
     *
     * <p>Returns {@code HoldNotFound} when the payment has no hold rows, {@code AlreadyConverted}
     * when every row is already converted (a redelivered webhook), and {@code Converted} with the
     * session ids otherwise. A capacity refusal surfaces as {@code
     * CapacityBelowAssignedException} and a unique-index collision (the student already holds
     * the session) as {@code StudentAlreadyAssignedException}; either rolls back the whole
     * conversion.
     *
     * @param type how the seats were acquired; persisted on every assignment row created here.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConvertOutcome convertAll(UUID paymentId, UUID studentId, AssignmentType type) {
        List<HeldSessionRow> rows = holdWriter.findByPaymentIdOrdered(paymentId);

        if (rows.isEmpty()) {
            return ConvertOutcome.HoldNotFound.INSTANCE;
        }
        if (rows.stream().allMatch(HeldSessionRow::converted)) {
            return ConvertOutcome.AlreadyConverted.INSTANCE;
        }

        List<UUID> sessionIds = rows.stream().map(HeldSessionRow::sessionId).toList();
        for (UUID sessionId : sessionIds) {
            convertOne(paymentId, sessionId, studentId, type);
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
    private void convertOne(UUID paymentId, UUID sessionId, UUID studentId, AssignmentType type) {
        holdWriter.markConverted(paymentId, sessionId);
        // Any refusal (capacity, or the student already holding the session)
        // propagates unchanged; the surrounding REQUIRES_NEW transaction rolls
        // back the markConverted call too — all-or-nothing.
        assignmentWriter.assertAssignment(sessionId, studentId, type);
    }
}
