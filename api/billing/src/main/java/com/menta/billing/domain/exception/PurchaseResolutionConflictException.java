package com.menta.billing.domain.exception;

import com.menta.billing.domain.model.PurchaseResolutionType;
import com.menta.shared.domain.exceptions.BusinessException;
import java.util.UUID;

/**
 * Thrown when an already resolved purchase receives a different resolution or note: a resolution
 * is final, only the identical request is an idempotent no-op.
 */
public class PurchaseResolutionConflictException extends BusinessException {

    private static final String ERROR_CODE = "PURCHASE_ALREADY_RESOLVED";

    public PurchaseResolutionConflictException(UUID purchaseId, PurchaseResolutionType existing) {
        super(ERROR_CODE, "Purchase " + purchaseId + " is already resolved as " + existing);
    }
}
