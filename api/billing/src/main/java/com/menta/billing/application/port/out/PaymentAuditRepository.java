package com.menta.billing.application.port.out;

import com.menta.billing.domain.model.PaymentAuditAction;
import com.menta.billing.domain.model.PaymentId;
import java.util.UUID;

/**
 * Append-only audit trail for admin decisions on a payment (#33, US-BILLING-005, design D4/C7):
 * approve, reject, and correction each write exactly one row. {@code reason} is nullable — an
 * approve carries none, a reject/correction carries the admin's motivo (a correction composes
 * evidence into it as {@code "{motivo} | evidencia: {evidence}"}).
 */
public interface PaymentAuditRepository {

    /**
     * Appends one audit row for an admin decision on a payment.
     *
     * <p>The JPA adapter joins the caller's transaction, so the row commits or rolls back together
     * with the payment's status change. {@code reason} may be {@code null}.
     */
    void append(PaymentId paymentId, UUID adminId, PaymentAuditAction action, String reason);
}
