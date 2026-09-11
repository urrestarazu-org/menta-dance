package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.CurrentSubscriptionResult;
import java.util.UUID;

/** Application-layer entry point for a user's current subscription (US-BILLING-004). */
public interface GetCurrentSubscriptionUseCase {

    /**
     * @throws com.menta.billing.domain.exception.NoSubscriptionException if the user has no
     *     {@code PENDING}, {@code ACTIVE}, or {@code EXPIRED} subscription (D1)
     */
    CurrentSubscriptionResult current(UUID userId);
}
