package com.menta.app.billing;

import com.menta.billing.application.port.in.MarkPurchaseExceptionPort;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Reason;
import org.springframework.stereotype.Component;

/**
 * Typed callable inside {@code api:app} that delegates the cross-module
 * call into Billing's {@link MarkPurchaseExceptionPort}. Mirrors the
 * pattern of {@code PhysicalCourseAvailabilityAdapter} / {@link
 * PhysicalCapacityAssignmentAdapter}.
 */
@Component
public class MarkPurchaseExceptionAdapter {

    private final MarkPurchaseExceptionPort markPurchaseExceptionPort;

    public MarkPurchaseExceptionAdapter(MarkPurchaseExceptionPort markPurchaseExceptionPort) {
        this.markPurchaseExceptionPort = markPurchaseExceptionPort;
    }

    public void markException(PaymentId paymentId, Reason reason) {
        markPurchaseExceptionPort.markException(paymentId, reason);
    }
}
