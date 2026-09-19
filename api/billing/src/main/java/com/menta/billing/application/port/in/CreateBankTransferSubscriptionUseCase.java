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

    SubscriptionCheckoutResult create(CreateSubscriptionCheckoutCommand command);
}
