package com.menta.physical.application.port.in;

import com.menta.physical.application.usecase.AssignmentOutcome;
import com.menta.physical.application.usecase.CapacityAssignments;
import com.menta.shared.physical.CapacityAssignmentCommand;
import com.menta.shared.physical.MultiSessionCapacityAssignmentCommand;

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

    /**
     * Ordered, all-or-nothing counterpart of {@link #assign} (design A3):
     * claims every session in {@code command.claims()}, in that exact
     * order, under one transaction. A failure on any claim aborts the
     * entire set — no partial subset is ever persisted.
     *
     * @return the assigned session ids, in claim order, when every claim
     *         succeeded.
     * @throws com.menta.physical.domain.exception.CapacityBelowAssignedException
     *         on the first claim that trips the capacity invariant OR a V7
     *         UNIQUE row collision; every earlier insert in this call is
     *         rolled back with it.
     */
    CapacityAssignments assignAll(MultiSessionCapacityAssignmentCommand command);
}
