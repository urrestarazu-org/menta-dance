package com.menta.bff.application.port.out;

import com.menta.bff.application.dto.OwnPayment;
import java.util.List;

/**
 * Port for the student's own payments in the Billing API (#363). Kept apart from
 * {@link BillingApiClient} (public plans, no access token at all) and from
 * {@link SubscriptionApiClient} (the subscription itself).
 *
 * <p>The call sends {@code Authorization: Bearer <accessToken>}; the token is never logged.
 * </p>
 */
public interface PaymentApiClient {

    /**
     * Lists the caller's payments ({@code GET /api/v1/billing/payments/me?limit=}).
     *
     * <p>Fails closed on every outcome that is not a usable 200: the history page only shows a
     * notice, so no status is told apart from another.
     * </p>
     *
     * @param accessToken caller's access token; must not be {@code null}
     * @param limit       most payments to return, 1 to 100
     * @return the payments, newest first; empty when there is none
     * @throws BillingApiClient.ServiceUnavailableException on any non-2xx answer, an unreadable or
     *         incomplete body, a timeout or a transport error
     */
    List<OwnPayment> listOwnPayments(String accessToken, int limit);
}
