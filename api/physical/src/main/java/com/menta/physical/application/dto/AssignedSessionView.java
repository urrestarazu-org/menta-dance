package com.menta.physical.application.dto;

import com.menta.shared.physical.AssignmentType;
import java.time.Instant;
import java.util.UUID;

/**
 * A session the student is entitled to attend (#356). {@code checkInOpen} is evaluated at the
 * instant the listing was built, with the same window the QR flow enforces.
 */
public record AssignedSessionView(
    UUID sessionId, UUID courseId, String courseName, String instructorName,
    Instant scheduledAt, boolean checkInOpen,
    AssignmentType assignmentType
) {
}
