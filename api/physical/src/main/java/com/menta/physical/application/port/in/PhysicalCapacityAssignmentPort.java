package com.menta.physical.application.port.in;

import com.menta.physical.application.usecase.AssignmentOutcome;
import com.menta.shared.physical.CapacityAssignmentCommand;

/**
 * Entry port Physical exposes for {@code api:app}'s outbox handler
 * (proposal §4; design §4.3).
 *
 * <p>The assign is fail-closed: both the read-time capacity check AND the
 * V7 {@code UNIQUE (session_id, student_id)} race trip the same
 * {@link com.menta.physical.domain.exception.CapacityBelowAssignedException}
 * which {@code api:app}'s handler routes to the {@code EXCEPTION} residual
 * terminal state. {@link AssignmentOutcome} carries the success / race-lost
 * verdict for callers that prefer a non-throwing shape; we still throw for
 * the fail-closed path so the in-port signature is uniform with the rest
 * of Physical's ports.</p>
 */
public interface PhysicalCapacityAssignmentPort {

    /**
     * @return {@link AssignmentOutcome#ASSIGNED} when the row was inserted,
     *         {@link AssignmentOutcome#RACE_LOST} when the read-time invariant
     *         trip happened but no INSERT was attempted (read-only trip —
     *         the {@code api:app} handler treats this as EXCEPTION-routable
     *         but does NOT propagate it as the live exception type).
     * @throws com.menta.physical.domain.exception.CapacityBelowAssignedException
     *         on capacity trip OR V7 UNIQUE row collision (consistent with
     *         design §5.3 — "the SOLE exception type").
     */
    AssignmentOutcome assign(CapacityAssignmentCommand command);
}
