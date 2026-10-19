package com.menta.billing.domain.exception;

import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Thrown by {@code MarkPurchaseExceptionUseCase} when an attempted
 * {@code markException} call would move a Purchase from
 * {@link FulfillmentStatus#ASSIGNED} to {@link FulfillmentStatus#EXCEPTION}
 * (ADR-0028 §Decisión: once assigned, no return to the residual state).
 *
 * <p>Carries the offending state pair so callers can log or surface a
 * useful diagnostic without re-querying the database.</p>
 */
public class IllegalPurchaseStateTransitionException extends BusinessException {

    private static final String ERROR_CODE = "ILLEGAL_PURCHASE_STATE_TRANSITION";

    public IllegalPurchaseStateTransitionException(
        PaymentId paymentId, FulfillmentStatus from, FulfillmentStatus to
    ) {
        super(ERROR_CODE,
            "Cannot transition purchase for paymentId=" + paymentId.getValue()
                + " from " + from + " to " + to);
    }
}
