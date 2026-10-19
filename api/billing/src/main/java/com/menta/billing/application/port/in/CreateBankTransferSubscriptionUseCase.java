package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.dto.SubscriptionCheckoutResult;

/**
 * Entry port for a bank-transfer subscription checkout (US-BILLING-003 escenario 1, design D3).
 *
 * <p>Shares {@link CreateSubscriptionCheckoutCommand}/{@link SubscriptionCheckoutResult} with
 * {@link CreateSubscriptionCheckoutUseCase} so {@link
 * com.menta.billing.application.usecase.RoutingCreateSubscriptionCheckoutUseCase} can dispatch to
 * either implementation without reshaping the request.</p>
 */
public interface CreateBankTransferSubscriptionUseCase {

    /**
     * Opens a bank-transfer subscription checkout and returns the transfer instructions.
     *
     * <p>The daily creation budget is consumed before anything else, and a refusal surfaces as
     * {@code BankTransferRateLimitedException}. Fails with {@code PlanNotAvailableException} when
     * the plan is missing or inactive, with {@code PaymentMethodNotAcceptedException} when the plan
     * does not accept the method, and with {@code SubscriptionAlreadyActiveException} when the user
     * already holds a current subscription. On success a payment in {@code
     * AwaitingManualVerification} and a pending subscription are saved.
     */
    SubscriptionCheckoutResult create(CreateSubscriptionCheckoutCommand command);
}
