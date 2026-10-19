package com.menta.physical.domain.model;

/**
 * How a check-in row was originally recorded. Bucketing at the domain level
 * keeps the door-side ingestion logic uncolored by presentation concerns:
 * a row remembers <em>how</em> it got there even though the MVP only wires
 * the QR variant. {@code MANUAL} is reserved for the operator keyboard flow
 * already in backlog as issue #45; anything else is a programming error
 * surfaced by {@link #valueOf(String)}.
 */
public enum AttendanceKind {
    QR,
    MANUAL
}
