package com.menta.virtual.infrastructure.transaction;

import com.menta.virtual.application.dto.UpdateVirtualCourseCommand;
import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.in.UpdateVirtualCourseUseCase;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Transactional decorator — see {@link TransactionalCreateVirtualCourseUseCase} for the rationale. */
public class TransactionalUpdateVirtualCourseUseCase implements UpdateVirtualCourseUseCase {

    private final UpdateVirtualCourseUseCase delegate;

    public TransactionalUpdateVirtualCourseUseCase(UpdateVirtualCourseUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public VirtualCourseManagementResult update(
        String courseId, UpdateVirtualCourseCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        return delegate.update(courseId, command, actingUserId, actingAsAdmin);
    }
}
