package com.menta.virtual.infrastructure.transaction;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.in.PublishVirtualCourseUseCase;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Transactional decorator — see {@link TransactionalCreateVirtualCourseUseCase} for the rationale. */
public class TransactionalPublishVirtualCourseUseCase implements PublishVirtualCourseUseCase {

    private final PublishVirtualCourseUseCase delegate;

    public TransactionalPublishVirtualCourseUseCase(PublishVirtualCourseUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public VirtualCourseManagementResult publish(String courseId, UUID actingUserId, boolean actingAsAdmin) {
        return delegate.publish(courseId, actingUserId, actingAsAdmin);
    }
}
