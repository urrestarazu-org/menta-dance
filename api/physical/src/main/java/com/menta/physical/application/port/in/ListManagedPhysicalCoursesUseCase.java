package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.PhysicalCourseManagementResult;
import java.util.List;
import java.util.UUID;

/**
 * US-PHYSICAL-005 escenario 2: list courses for management — every status,
 * unlike the public catalog's ACTIVE-only read (#40/#95).
 */
public interface ListManagedPhysicalCoursesUseCase {

    /** ADMIN sees every course; INSTRUCTOR sees only courses they own. */
    List<PhysicalCourseManagementResult> list(UUID actingUserId, boolean actingAsAdmin);
}
