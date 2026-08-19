package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.BatchCreatePhysicalSessionsCommand;
import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import java.util.List;
import java.util.UUID;

public interface BatchCreatePhysicalSessionsUseCase {

    List<PhysicalSessionManagementResult> createBatch(
        String courseId, BatchCreatePhysicalSessionsCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
