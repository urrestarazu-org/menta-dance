package com.menta.virtual.application.port.out;

import java.util.UUID;

/**
 * Append-only audit trail for the actions on videos of the library (#841). It has its own table, not
 * {@code virtual_course_audit}: a video belongs to no course (a lesson of any course may use it, or none), and that
 * table's {@code course_id} is mandatory. Same shape otherwise: actor, action, previous and new value.
 */
public interface VirtualVideoAuditRepository {

    /**
     * Appends one audit row.
     *
     * @param previousValue a human-readable snapshot before the change, or {@code null} when there is none
     * @param newValue a human-readable snapshot after the change, or {@code null} for a deletion
     */
    void append(String videoId, UUID actorId, String action, String previousValue, String newValue);
}
