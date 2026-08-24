package com.menta.physical.application.port.out;

import com.menta.physical.domain.model.SessionId;
import java.util.UUID;

/**
 * Read-only port over {@code physical_capacity_assignments} for the
 * check-in flow (US-PHYSICAL-001 escenarios 1, 3, 7). A row in that table
 * <strong>is</strong> a confirmed assignment — there is no status column to
 * filter on (see {@code V7__physical_courses.sql}: cancellation deletes the
 * row instead of flagging it), so {@code existsConfirmedAssignment} is a
 * plain existence check, not a status comparison.
 *
 * <p>No write method here: this issue never creates or removes assignments,
 * that belongs to the future checkout/capacity flow (#41). Reusing the
 * plural {@code PhysicalCapacityAssignmentRepository} name for a read-only
 * port keeps it symmetric with {@link AttendanceRepository}'s naming even
 * though the write side is intentionally absent for now.</p>
 */
public interface PhysicalCapacityAssignmentRepository {

    boolean existsConfirmedAssignment(SessionId sessionId, UUID studentId);
}
