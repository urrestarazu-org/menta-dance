package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ListManagedPhysicalSessionsUseCase {

    /**
     * @param from lower bound (inclusive), or {@code null} for an
     *     effectively unbounded lower end — the implementation decides what
     *     "unbounded" resolves to.
     * @param to upper bound (exclusive), or {@code null} for an effectively
     *     unbounded upper end.
     */
    List<PhysicalSessionManagementResult> list(
        String courseId, Instant from, Instant to, UUID actingUserId, boolean actingAsAdmin
    );
}
