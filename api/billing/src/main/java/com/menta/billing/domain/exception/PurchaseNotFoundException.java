package com.menta.billing.domain.exception;

import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Distinct from {@code IllegalPurchaseStateTransitionException} — the
 * handler API can fail with this when the producer side never persisted
 * a row for some reason (test scaffolding, an upstream bug, or a future
 * migration that drops the row). Surfaces to the caller with the missing
 * status so the diagnostic is immediately actionable.
 */
public class PurchaseNotFoundException extends BusinessException {

    private static final String ERROR_CODE = "PURCHASE_NOT_FOUND";

    public PurchaseNotFoundException() {
        super(ERROR_CODE, "No purchase found for the supplied paymentId");
    }

    public PurchaseNotFoundException(FulfillmentStatus assumed) {
        super(ERROR_CODE,
            "No purchase found for the supplied paymentId (assumed status=" + assumed + ")");
    }
}
