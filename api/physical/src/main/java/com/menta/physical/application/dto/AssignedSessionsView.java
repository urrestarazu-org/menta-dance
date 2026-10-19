package com.menta.physical.application.dto;

import java.time.Instant;
import java.util.List;

/** The student's upcoming sessions over the half-open range {@code [from, to)} (#356), oldest first. */
public record AssignedSessionsView(Instant from, Instant to, List<AssignedSessionView> sessions) {
}
