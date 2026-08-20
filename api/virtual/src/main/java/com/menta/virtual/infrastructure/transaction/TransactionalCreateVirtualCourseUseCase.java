package com.menta.virtual.infrastructure.transaction;

import com.menta.virtual.application.dto.CreateVirtualCourseCommand;
import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.in.CreateVirtualCourseUseCase;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator (US-VIRTUAL-006, #54 review follow-up): the course
 * creation and its audit row must share a single commit — the delegate's own
 * repository calls each open an independent transaction otherwise, so a
 * failure between them would leave an unaudited mutation (mirrors {@code
 * auth}'s {@code TransactionalActivateAccountUseCase}).
 */
public class TransactionalCreateVirtualCourseUseCase implements CreateVirtualCourseUseCase {

    private final CreateVirtualCourseUseCase delegate;

    public TransactionalCreateVirtualCourseUseCase(CreateVirtualCourseUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public VirtualCourseManagementResult create(
        CreateVirtualCourseCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        return delegate.create(command, actingUserId, actingAsAdmin);
    }
}
