package com.menta.physical.infrastructure.persistence.repository;

import com.menta.shared.physical.AssignmentType;
import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data interface projection for the student's assigned-sessions JPQL query (#356);
 * {@code PhysicalCapacityAssignmentRepositoryAdapter} maps it to the application-layer
 * {@code AssignedSessionRow}.
 */
public interface AssignedSessionRowProjection {

    /** Id of the assigned session. */
    UUID getSessionId();

    /** Id of the session's course. */
    UUID getCourseId();

    /** Title of the session's course. */
    String getCourseName();

    /** Display name of the professor who owns the session's course. */
    String getInstructorName();

    /** Instant at which the session starts. */
    Instant getScheduledAt();

    /** How the student obtained the assignment (monthly plan or individual purchase). */
    AssignmentType getAssignmentType();
}
