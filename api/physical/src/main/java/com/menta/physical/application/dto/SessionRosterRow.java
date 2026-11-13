package com.menta.physical.application.dto;

import com.menta.physical.domain.model.AttendanceKind;
import com.menta.shared.physical.AssignmentType;
import java.time.Instant;
import java.util.UUID;

/**
 * One confirmed assignment of a session, as returned by {@code PhysicalCapacityAssignmentRepository#findRoster}
 * (#355), {@code LEFT JOIN}ed with the student's check-in for that same session.
 *
 * <p>{@code checkedInAt} and {@code checkInKind} are {@code null} exactly when the student has not checked in.
 */
public record SessionRosterRow(
    UUID studentId, AssignmentType assignmentType, Instant checkedInAt, AttendanceKind checkInKind
) {
}
