package com.menta.virtual.application.port.out;

import com.menta.virtual.domain.model.CourseId;
import java.util.UUID;

/**
 * Append-only audit trail for course/module/lesson management operations
 * (US-VIRTUAL-006: "auditar todas las operaciones... incluido actor, curso,
 * acción y valores anterior/nuevo"). Deliberately a table, not the log-based
 * pattern {@code LoginAttemptAuditPort} uses in {@code auth} — these are
 * admin operations, not a per-request hot path, and the acceptance criteria
 * requires queryable before/after values a log line can't give structured
 * access to.
 */
public interface VirtualCourseAuditRepository {

    /**
     * @param previousValue a human-readable snapshot before the change, or
     *     {@code null} for a creation (there is no "before").
     * @param newValue a human-readable snapshot after the change.
     */
    void append(CourseId courseId, UUID actorId, String action, String previousValue, String newValue);
}
