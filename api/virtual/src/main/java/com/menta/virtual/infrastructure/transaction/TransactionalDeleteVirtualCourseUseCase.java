package com.menta.virtual.infrastructure.transaction;

import com.menta.virtual.application.port.in.DeleteVirtualCourseUseCase;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator — critical here specifically: the delegate deletes
 * lessons, then modules, then the course, then writes the audit row, as four
 * separate repository calls. Without this wrapper a failure partway through
 * (e.g. the course delete itself) would leave orphaned children removed but
 * the course row still present, or a fully-deleted course with no audit
 * trail. See {@link TransactionalCreateVirtualCourseUseCase} for the general
 * rationale.
 */
public class TransactionalDeleteVirtualCourseUseCase implements DeleteVirtualCourseUseCase {

    private final DeleteVirtualCourseUseCase delegate;

    public TransactionalDeleteVirtualCourseUseCase(DeleteVirtualCourseUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public void delete(String courseId, UUID actingUserId, boolean actingAsAdmin) {
        delegate.delete(courseId, actingUserId, actingAsAdmin);
    }
}
