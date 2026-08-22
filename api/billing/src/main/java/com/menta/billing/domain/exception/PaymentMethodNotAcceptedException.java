package com.menta.billing.domain.exception;

import com.menta.billing.domain.model.PaymentMethod;
import com.menta.shared.domain.exceptions.BusinessException;
import java.util.Set;

/**
 * The plan is {@code ACTIVE} but does not accept the requested payment
 * method (US-BILLING-010 escenario 4b). Carries the accepted set so the
 * response can tell the client what would work — unlike the plan lookup,
 * this leaks nothing a correct client did not already read from the public
 * catalog.
 */
public class PaymentMethodNotAcceptedException extends BusinessException {

    private static final String ERROR_CODE = "PAYMENT_METHOD_NOT_ACCEPTED";

    private final Set<PaymentMethod> acceptedPaymentMethods;

    public PaymentMethodNotAcceptedException(
        PaymentMethod requested, Set<PaymentMethod> acceptedPaymentMethods
    ) {
        super(
            ERROR_CODE,
            "Plan does not accept payment method " + requested + "; accepted: " + acceptedPaymentMethods
        );
        this.acceptedPaymentMethods = Set.copyOf(acceptedPaymentMethods);
    }

    public Set<PaymentMethod> getAcceptedPaymentMethods() {
        return acceptedPaymentMethods;
    }
}
