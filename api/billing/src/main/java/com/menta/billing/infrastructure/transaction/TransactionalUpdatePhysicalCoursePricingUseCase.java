package com.menta.billing.infrastructure.transaction;

import com.menta.billing.application.dto.PhysicalCoursePricingResult;
import com.menta.billing.application.dto.UpdatePhysicalCoursePricingCommand;
import com.menta.billing.application.port.in.UpdatePhysicalCoursePricingUseCase;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator — the pricing save and its audit revision append
 * must commit atomically (US-BILLING-009: "incrementa versión y agrega una
 * revisión append-only"). Mirrors {@code TransactionalReceiveWebhookUseCase}
 * and Virtual's {@code TransactionalUpdateVirtualCourseUseCase}.
 */
public class TransactionalUpdatePhysicalCoursePricingUseCase implements UpdatePhysicalCoursePricingUseCase {

    private final UpdatePhysicalCoursePricingUseCase delegate;

    public TransactionalUpdatePhysicalCoursePricingUseCase(UpdatePhysicalCoursePricingUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public PhysicalCoursePricingResult update(
        String courseId, UpdatePhysicalCoursePricingCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        return delegate.update(courseId, command, actingUserId, actingAsAdmin);
    }
}
