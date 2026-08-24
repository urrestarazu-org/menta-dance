package com.menta.physical.application.usecase;

import com.menta.physical.application.port.in.PhysicalCapacityAssignmentPort;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentWriter;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.physical.domain.exception.SessionNotFoundException;
import com.menta.physical.domain.model.PhysicalSession;
import com.menta.physical.domain.model.SessionId;
import com.menta.shared.physical.CapacityAssignmentCommand;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the live capacity invariant (no cached counter — see
 * {@code PhysicalSession}'s Javadoc) and INSERTs when available, throws
 * {@link CapacityBelowAssignedException} otherwise.
 *
 * <ul>
 *   <li>Read-time trip — propagation REQUIRED first reads the session through
 *       {@link com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository#findByIdWithAvailability}
 *       which already returns {@code assignedSpots} (a correlated
 *       {@code COUNT(*)} subquery).</li>
 *   <li>UNIQUE race trip — V7 {@code UNIQUE (session_id, student_id)}
 *       collision: caught and rethrown as
 *       {@link CapacityBelowAssignedException} so {@code api:app} can route
 *       to {@code EXCEPTION} via the same catch it already has.</li>
 * </ul>
 *
 * <p>Single transaction (REQUIRED) per design §5.3 — joins the
 * {@code api:app} outbox handler's REQUIRES_NEW transaction.</p>
 */
@Component
public class AssignCapacityUseCase implements PhysicalCapacityAssignmentPort {

    private final PhysicalSessionRepository sessionRepository;
    private final PhysicalCapacityAssignmentWriter assignmentWriter;

    public AssignCapacityUseCase(
        PhysicalSessionRepository sessionRepository,
        PhysicalCapacityAssignmentWriter assignmentWriter
    ) {
        this.sessionRepository = sessionRepository;
        this.assignmentWriter = assignmentWriter;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public AssignmentOutcome assign(CapacityAssignmentCommand command) {
        SessionId sessionId = SessionId.of(command.sessionId());
        UUID studentId = command.studentId();
        PhysicalSession session = sessionRepository.findById(sessionId)
            .orElseThrow(SessionNotFoundException::new);

        if (session.getAssignedSpots() >= session.getCapacity()) {
            throw new CapacityBelowAssignedException();
        }

        try {
            assignmentWriter.assertAssignment(sessionId.getValue(), studentId);
        } catch (DataIntegrityViolationException uniqueRace) {
            // V7 UNIQUE (session_id, student_id) — the second concurrent
            // INSERT lost. Per design §5.3, route as CapacityBelowAssigned
            // so api:app funnels it into the EXCEPTION residual.
            throw new CapacityBelowAssignedException();
        }

        return AssignmentOutcome.ASSIGNED.INSTANCE;
    }
}
