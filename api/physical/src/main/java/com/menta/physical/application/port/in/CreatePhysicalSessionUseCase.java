package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.CreatePhysicalSessionCommand;
import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import java.util.UUID;

/** In-port for creating a single session of a physical course (US-PHYSICAL-006). */
public interface CreatePhysicalSessionUseCase {

    /**
     * Creates one {@code SCHEDULED} session for a course the caller may manage.
     *
     * <p>The date and start time are interpreted as UTC. When the command carries no capacity, the
     * course's own capacity is used.
     */
    PhysicalSessionManagementResult create(
        String courseId, CreatePhysicalSessionCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
