package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** In-port for the management view of a course's sessions (US-PHYSICAL-006), cancelled ones included. */
public interface ListManagedPhysicalSessionsUseCase {

    /**
     * Lists the sessions of a course for its owner or an administrator, whatever their status.
     *
     * <p>Each session reports live assigned and held counts.
     *
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
