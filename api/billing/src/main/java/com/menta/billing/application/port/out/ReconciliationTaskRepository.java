package com.menta.billing.application.port.out;

import com.menta.billing.domain.model.PaymentId;
import java.time.Instant;
import java.util.UUID;

/**
 * Resolution of the {@code billing_reconciliation_tasks} row tied to a payment (#33,
 * US-BILLING-005, design D2/C11). A return of {@code 0} is not an error: {@code
 * WebhookVerificationWorker} writes tasks with a {@code null} payment id when no local payment
 * matched, so a payment can legitimately have no tied task, and the correction stands either way.
 */
public interface ReconciliationTaskRepository {

    /**
     * Marks every open reconciliation task of a payment as resolved by the given admin at the given
     * instant, and returns how many rows changed.
     *
     * <p>Re-running it is harmless because tasks that are already resolved are skipped.
     */
    int resolveOpenByPaymentId(PaymentId paymentId, Instant at, UUID adminId);
}
