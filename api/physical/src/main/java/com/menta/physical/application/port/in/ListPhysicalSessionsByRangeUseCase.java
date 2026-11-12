package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalRangeSessionView;
import java.time.Instant;
import java.util.List;

/** In-port for the staff listing of every session in an instant range, across all courses (#355). */
public interface ListPhysicalSessionsByRangeUseCase {

    /**
     * Lists the sessions whose start is in the half-open range {@code [from, to)}, whatever their status
     * or course, ordered by start time and then by course name. Both bounds are UTC instants: the
     * caller converts its local day to the range, the backend never applies a time zone.
     *
     * @throws com.menta.physical.domain.exception.InvalidSessionRangeException when {@code from} is not
     *     before {@code to} or the range spans more than 7 days
     */
    List<PhysicalRangeSessionView> list(Instant from, Instant to);
}
