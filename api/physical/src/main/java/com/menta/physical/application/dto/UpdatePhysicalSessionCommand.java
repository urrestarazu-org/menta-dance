package com.menta.physical.application.dto;

import com.menta.physical.domain.model.SessionStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/** Partial update — an empty {@link Optional} means "not present in this PATCH, leave unchanged". */
public record UpdatePhysicalSessionCommand(
    Optional<LocalDate> date,
    Optional<LocalTime> startTime,
    Optional<Integer> capacity,
    Optional<String> notes,
    Optional<SessionStatus> status
) {
}
