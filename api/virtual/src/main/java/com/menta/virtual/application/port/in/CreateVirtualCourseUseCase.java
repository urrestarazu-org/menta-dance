package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.CreateVirtualCourseCommand;
import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import java.util.UUID;

/** Inbound port for creating a course on behalf of an ADMIN or an INSTRUCTOR (US-VIRTUAL-006). */
public interface CreateVirtualCourseUseCase {

    /**
     * Creates a {@code DRAFT} course and appends a {@code CREATE_COURSE} audit row.
     *
     * <p>The owner is the requested {@code professorId} when acting as admin and the actor
     * otherwise; an instructor naming another professor is rejected with {@code
     * ProfessorMismatchException}.
     */
    VirtualCourseManagementResult create(CreateVirtualCourseCommand command, UUID actingUserId, boolean actingAsAdmin);
}
