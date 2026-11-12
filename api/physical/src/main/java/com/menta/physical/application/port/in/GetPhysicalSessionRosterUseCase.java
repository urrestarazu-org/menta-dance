package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalSessionRosterView;

/** In-port for the staff roster of one session: who holds a confirmed spot and who already checked in (#355). */
public interface GetPhysicalSessionRosterUseCase {

    /**
     * Returns the session header and its students, ordered by display name and then by student id.
     *
     * @param sessionId canonical UUID text of the session
     * @throws IllegalArgumentException when {@code sessionId} is not a valid UUID
     * @throws com.menta.physical.domain.exception.SessionNotFoundException when no session has that id
     */
    PhysicalSessionRosterView get(String sessionId);
}
