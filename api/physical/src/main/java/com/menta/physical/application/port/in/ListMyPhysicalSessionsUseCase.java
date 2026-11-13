package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.AssignedSessionsView;
import java.time.Instant;
import java.util.UUID;

/** In-port for the student's own sessions in an instant range (#356, #729, US-PHYSICAL-001). */
public interface ListMyPhysicalSessionsUseCase {

    /**
     * Lists the sessions whose start is in the half-open range {@code [from, to)} where the student
     * holds a confirmed capacity assignment. Both bounds are UTC instants: the caller converts its
     * local day or month to the range, the backend never applies a time zone.
     *
     * <p>Holds and unpaid purchases are not assignments and never appear; cancelled sessions are
     * left out. Results are ordered by scheduled start ascending.
     *
     * @throws com.menta.physical.domain.exception.InvalidSessionRangeException when {@code from} is not
     *     before {@code to} or the range spans more than 92 days
     */
    AssignedSessionsView list(UUID studentId, Instant from, Instant to);
}
