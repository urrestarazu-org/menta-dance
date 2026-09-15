package com.menta.app.billing;

import com.menta.billing.application.port.in.MarkPurchaseAssignedPort;
import com.menta.billing.domain.model.PaymentId;
import org.springframework.stereotype.Component;

/**
 * Typed callable inside {@code api:app} that delegates the cross-module
 * call into Billing's {@link MarkPurchaseAssignedPort}. Mirrors the pattern
 * of {@link MarkPurchaseExceptionAdapter} / {@link PhysicalCapacityAssignmentAdapter}.
 */
@Component
public class MarkPurchaseAssignedAdapter {

    private final MarkPurchaseAssignedPort markPurchaseAssignedPort;

    public MarkPurchaseAssignedAdapter(MarkPurchaseAssignedPort markPurchaseAssignedPort) {
        this.markPurchaseAssignedPort = markPurchaseAssignedPort;
    }

    public void markAssigned(PaymentId paymentId) {
        markPurchaseAssignedPort.markAssigned(paymentId);
    }
}
