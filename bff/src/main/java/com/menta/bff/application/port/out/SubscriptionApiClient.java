package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.CurrentSubscription;

/**
 * Port for the authenticated subscription endpoints of the Billing API. Kept apart from
 * {@link BillingApiClient} on purpose: that port promises its public plans call has no
 * access-token parameter at all, and these calls need one.
 *
 * <p>Every method sends {@code Authorization: Bearer <accessToken>}; the token is never logged.
 * Failures are closed: anything that is not a documented answer surfaces as
 * {@link BillingApiClient.ServiceUnavailableException}.
 * </p>
 */
public interface SubscriptionApiClient {

    /**
     * Reads the caller's current subscription ({@code GET /api/v1/billing/subscriptions/me}).
     *
     * @param accessToken caller's access token
     * @return the current subscription; {@link CurrentSubscription.None} on 404 {@code NO_SUBSCRIPTION}
     * @throws BillingApiClient.ServiceUnavailableException on any other outcome: 404 with another
     *         code, 401, 429, 5xx, an unexpected or incomplete answer, or a transport error
     */
    CurrentSubscription getCurrentSubscription(String accessToken);
}
