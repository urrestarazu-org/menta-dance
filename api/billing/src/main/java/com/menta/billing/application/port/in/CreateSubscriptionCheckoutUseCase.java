package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.dto.SubscriptionCheckoutResult;

/** Entry port for {@code POST /api/v1/billing/subscriptions} (US-BILLING-010). */
public interface CreateSubscriptionCheckoutUseCase {

    SubscriptionCheckoutResult create(CreateSubscriptionCheckoutCommand command);
}
