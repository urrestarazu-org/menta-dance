package com.menta.physical.application.dto;

/**
 * Attendance of a student on a session roster (#355): {@code PRESENT} once a check-in exists, {@code PENDING}
 * otherwise. Derived, never stored; it says nothing about absence because the session may not have happened yet.
 */
public enum RosterAttendanceStatus {
    PRESENT,
    PENDING
}
