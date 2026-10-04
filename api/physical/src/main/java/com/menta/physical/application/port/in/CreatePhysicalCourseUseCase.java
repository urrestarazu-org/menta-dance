package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.CreatePhysicalCourseCommand;
import com.menta.physical.application.dto.PhysicalCourseManagementResult;
import java.util.UUID;

/** US-PHYSICAL-005 escenario 1: create a recurring physical course. */
public interface CreatePhysicalCourseUseCase {

    /**
     * Creates a recurring course that starts {@code ACTIVE}.
     *
     * <p>Administrators may name any professor, defaulting to themselves; an instructor may only
     * create courses for themselves and is rejected with {@code ProfessorMismatchException}
     * otherwise.
     *
     * @param actingUserId the authenticated caller's user id.
     * @param actingAsAdmin {@code true} when the caller holds ADMIN; {@code
     *     false} for INSTRUCTOR. This module has no visibility into {@code
     *     api:auth}'s {@code Role} enum (module boundary,
     *     docs/25-ARCHITECTURE-RULES.md) — the caller (the web layer, which
     *     reads Spring Security authorities) resolves the role into this
     *     boolean before calling in.
     */
    PhysicalCourseManagementResult create(
        CreatePhysicalCourseCommand command, UUID actingUserId, boolean actingAsAdmin);
}
