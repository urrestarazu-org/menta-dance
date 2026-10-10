package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.AssignedSessionsView;
import java.util.UUID;

/** In-port for the student's own upcoming sessions (#356, US-PHYSICAL-001). */
public interface ListMyPhysicalSessionsUseCase {

    /**
     * Lists the sessions, from the start of today in the academy's zone through a bounded number
     * of days ahead, where the student holds a confirmed capacity assignment.
     *
     * <p>Holds and unpaid purchases are not assignments and never appear; cancelled sessions are
     * left out. Results are ordered by scheduled start ascending.
     */
    AssignedSessionsView list(UUID studentId);
}
