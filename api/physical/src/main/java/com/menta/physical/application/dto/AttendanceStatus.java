package com.menta.physical.application.dto;

/**
 * Derived, never stored (#39, US-PHYSICAL-002, design D5/C6) — lives in {@code application.dto},
 * not {@code domain}, the same criterion {@code CourseProgressView} uses in {@code api/virtual}:
 * it has no identity and no invariant, only a live projection of whether a
 * {@code physical_attendances} row exists for an assignment.
 */
public enum AttendanceStatus {
    ATTENDED,
    ABSENT
}
