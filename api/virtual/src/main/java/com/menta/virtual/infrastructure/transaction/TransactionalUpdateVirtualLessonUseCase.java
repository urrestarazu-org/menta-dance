package com.menta.virtual.infrastructure.transaction;

import com.menta.virtual.application.dto.UpdateVirtualLessonCommand;
import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import com.menta.virtual.application.port.in.UpdateVirtualLessonUseCase;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Transactional decorator — see {@link TransactionalCreateVirtualCourseUseCase} for the rationale. */
public class TransactionalUpdateVirtualLessonUseCase implements UpdateVirtualLessonUseCase {

    private final UpdateVirtualLessonUseCase delegate;

    public TransactionalUpdateVirtualLessonUseCase(UpdateVirtualLessonUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public VirtualLessonManagementResult update(
        String lessonId, UpdateVirtualLessonCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        return delegate.update(lessonId, command, actingUserId, actingAsAdmin);
    }
}
