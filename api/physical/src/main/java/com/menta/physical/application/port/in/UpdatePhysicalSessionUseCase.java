package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import com.menta.physical.application.dto.UpdatePhysicalSessionCommand;
import java.util.UUID;

/** In-port for partially updating a physical session (US-PHYSICAL-006). */
public interface UpdatePhysicalSessionUseCase {

    /**
     * Applies a partial update to a session.
     *
     * <p>Authorization goes through the session's parent course (administrators and the owning
     * professor only). A session that already happened cannot be changed, and a capacity below the
     * spots already assigned is rejected. Among status values only {@code CANCELLED} has an effect;
     * any other value is ignored.
     */
    PhysicalSessionManagementResult update(
        String sessionId, UpdatePhysicalSessionCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
