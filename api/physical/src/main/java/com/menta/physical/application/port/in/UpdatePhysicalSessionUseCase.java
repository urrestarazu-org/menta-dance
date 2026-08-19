package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import com.menta.physical.application.dto.UpdatePhysicalSessionCommand;
import java.util.UUID;

public interface UpdatePhysicalSessionUseCase {

    PhysicalSessionManagementResult update(
        String sessionId, UpdatePhysicalSessionCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
