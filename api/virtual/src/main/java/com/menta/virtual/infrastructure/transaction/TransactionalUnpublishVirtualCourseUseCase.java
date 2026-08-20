package com.menta.virtual.infrastructure.transaction;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.in.UnpublishVirtualCourseUseCase;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Transactional decorator — see {@link TransactionalCreateVirtualCourseUseCase} for the rationale. */
public class TransactionalUnpublishVirtualCourseUseCase implements UnpublishVirtualCourseUseCase {

    private final UnpublishVirtualCourseUseCase delegate;

    public TransactionalUnpublishVirtualCourseUseCase(UnpublishVirtualCourseUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public VirtualCourseManagementResult unpublish(String courseId, UUID actingUserId, boolean actingAsAdmin) {
        return delegate.unpublish(courseId, actingUserId, actingAsAdmin);
    }
}
