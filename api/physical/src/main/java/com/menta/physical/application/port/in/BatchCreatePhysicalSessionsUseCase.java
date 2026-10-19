package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.BatchCreatePhysicalSessionsCommand;
import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import java.util.List;
import java.util.UUID;

/** In-port for generating the recurring sessions of a course over a date range (US-PHYSICAL-006). */
public interface BatchCreatePhysicalSessionsUseCase {

    /**
     * Generates one session for every date in the range that falls on the course's weekday.
     *
     * <p>Each session starts at the course's start time (interpreted as UTC), takes the course
     * capacity and begins {@code SCHEDULED}. Both bounds of the range are inclusive, an inverted
     * range generates nothing, and the whole batch is saved together.
     */
    List<PhysicalSessionManagementResult> createBatch(
        String courseId, BatchCreatePhysicalSessionsCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
