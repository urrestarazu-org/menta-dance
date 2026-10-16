package com.menta.physical.application.dto;

import com.menta.shared.physical.AssignmentType;
import java.time.Instant;
import java.util.UUID;

/**
 * One session the student holds a confirmed assignment for, as returned by
 * {@code PhysicalCapacityAssignmentRepository#findAssignedSessions} (#356).
 */
public record AssignedSessionRow(
    UUID sessionId, UUID courseId, String courseName, String instructorName, Instant scheduledAt,
    AssignmentType assignmentType
) {
}
