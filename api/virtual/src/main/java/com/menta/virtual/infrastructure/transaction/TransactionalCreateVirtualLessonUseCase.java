package com.menta.virtual.infrastructure.transaction;

import com.menta.virtual.application.dto.CreateVirtualLessonCommand;
import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import com.menta.virtual.application.port.in.CreateVirtualLessonUseCase;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Transactional decorator — see {@link TransactionalCreateVirtualCourseUseCase} for the rationale. */
public class TransactionalCreateVirtualLessonUseCase implements CreateVirtualLessonUseCase {

    private final CreateVirtualLessonUseCase delegate;

    public TransactionalCreateVirtualLessonUseCase(CreateVirtualLessonUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public VirtualLessonManagementResult create(
        String moduleId, CreateVirtualLessonCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        return delegate.create(moduleId, command, actingUserId, actingAsAdmin);
    }
}
