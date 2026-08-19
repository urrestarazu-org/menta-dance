package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ListManagedPhysicalSessionsUseCase {

    List<PhysicalSessionManagementResult> list(
        String courseId, Instant from, Instant to, UUID actingUserId, boolean actingAsAdmin
    );
}
