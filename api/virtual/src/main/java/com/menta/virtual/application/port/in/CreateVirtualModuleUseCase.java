package com.menta.virtual.application.port.in;

import com.menta.virtual.application.dto.CreateVirtualModuleCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import java.util.UUID;

/** Inbound port for adding a module to a course (US-VIRTUAL-006). */
public interface CreateVirtualModuleUseCase {

    /**
     * Creates a module in the course and appends a {@code CREATE_MODULE} audit row.
     *
     * <p>An absent order defaults to the course's current module count. Fails with {@code
     * CourseNotFoundException} for an unknown course and {@code CourseNotOwnedException} when a
     * non-admin actor does not own it.
     */
    VirtualModuleManagementResult create(
        String courseId, CreateVirtualModuleCommand command, UUID actingUserId, boolean actingAsAdmin
    );
}
