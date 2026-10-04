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

    /**
     * Moves the purchase behind the payment to the assigned state once all its sessions were claimed.
     *
     * <p>Pure delegation: the transition rules, and any exception raised when the payment is
     * unknown, belong to the billing port and propagate to the caller unchanged.</p>
     *
     * @param paymentId the payment whose purchase was fully assigned
     */
    public void markAssigned(PaymentId paymentId) {
        markPurchaseAssignedPort.markAssigned(paymentId);
    }
}
