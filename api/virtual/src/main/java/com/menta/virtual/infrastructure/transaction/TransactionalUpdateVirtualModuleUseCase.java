package com.menta.virtual.infrastructure.transaction;

import com.menta.virtual.application.dto.UpdateVirtualModuleCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.in.UpdateVirtualModuleUseCase;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Transactional decorator — see {@link TransactionalCreateVirtualCourseUseCase} for the rationale. */
public class TransactionalUpdateVirtualModuleUseCase implements UpdateVirtualModuleUseCase {

    private final UpdateVirtualModuleUseCase delegate;

    public TransactionalUpdateVirtualModuleUseCase(UpdateVirtualModuleUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public VirtualModuleManagementResult update(
        String moduleId, UpdateVirtualModuleCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        return delegate.update(moduleId, command, actingUserId, actingAsAdmin);
    }
}
