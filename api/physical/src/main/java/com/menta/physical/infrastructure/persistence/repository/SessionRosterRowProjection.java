package com.menta.physical.infrastructure.persistence.repository;

import com.menta.shared.physical.AssignmentType;
import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data interface projection for the session roster JPQL query (#355); {@code
 * PhysicalCapacityAssignmentRepositoryAdapter} maps it to the application-layer {@code SessionRosterRow}.
 */
public interface SessionRosterRowProjection {

    /** Id of the student holding the assignment. */
    UUID getStudentId();

    /** How the student obtained the assignment (monthly plan or individual purchase). */
    AssignmentType getAssignmentType();

    /** Instant of the student's check-in for the session, or {@code null} when there is none. */
    Instant getCheckedInAt();

    /** Origin of that check-in ({@code QR} or {@code MANUAL}), or {@code null} when there is none. */
    String getCheckInKind();
}
