package com.menta.virtual.infrastructure.transaction;

import com.menta.virtual.application.dto.CreateVirtualModuleCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.in.CreateVirtualModuleUseCase;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Transactional decorator — see {@link TransactionalCreateVirtualCourseUseCase} for the rationale. */
public class TransactionalCreateVirtualModuleUseCase implements CreateVirtualModuleUseCase {

    private final CreateVirtualModuleUseCase delegate;

    public TransactionalCreateVirtualModuleUseCase(CreateVirtualModuleUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public VirtualModuleManagementResult create(
        String courseId, CreateVirtualModuleCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        return delegate.create(courseId, command, actingUserId, actingAsAdmin);
    }
}
