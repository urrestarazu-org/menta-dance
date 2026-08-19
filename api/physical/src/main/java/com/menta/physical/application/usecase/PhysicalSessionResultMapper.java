package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.PhysicalSessionManagementResult;
import com.menta.physical.domain.model.PhysicalSession;

final class PhysicalSessionResultMapper {

    private PhysicalSessionResultMapper() {
    }

    static PhysicalSessionManagementResult toResult(PhysicalSession session) {
        return new PhysicalSessionManagementResult(
            session.getId().toString(),
            session.getCourseId().toString(),
            session.getScheduledAt().toString(),
            session.getCapacity(),
            session.getAssignedSpots(),
            session.getActiveCapacityHolds(),
            session.getAvailableSpots(),
            session.getStatus().name(),
            session.getNotes()
        );
    }
}
