package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.CreatePhysicalSessionCommand;
import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import java.util.UUID;

public interface CreatePhysicalSessionUseCase {

    PhysicalSessionManagementResult create(
        String courseId, CreatePhysicalSessionCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
