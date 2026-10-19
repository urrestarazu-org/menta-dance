package com.menta.virtual.infrastructure.transaction;

import com.menta.virtual.application.dto.ReorderVirtualModulesCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.in.ReorderVirtualModulesUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator — the delegate reassigns {@code display_order} for
 * every module via {@code saveAll} and then writes one audit row; without
 * this wrapper the two are two independent commits. See {@link
 * TransactionalCreateVirtualCourseUseCase} for the general rationale.
 */
public class TransactionalReorderVirtualModulesUseCase implements ReorderVirtualModulesUseCase {

    private final ReorderVirtualModulesUseCase delegate;

    public TransactionalReorderVirtualModulesUseCase(ReorderVirtualModulesUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public List<VirtualModuleManagementResult> reorder(
        String courseId, ReorderVirtualModulesCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        return delegate.reorder(courseId, command, actingUserId, actingAsAdmin);
    }
}
