package com.menta.billing.application.port.out;

import com.menta.billing.application.dto.RateLimitDecision;

/**
 * Scraping-prevention budget for the public plans endpoints (US-BILLING-001:
 * 60 requests/minute per IP). Unlike auth's login budget, every request
 * counts equally — there is no "successful" outcome to exempt — so this port
 * has a single atomic consume, mirroring {@code ActivationRateLimitPort}
 * rather than the split check/recordFailure shape login uses.
 */
public interface BillingPlansRateLimitPort {

    RateLimitDecision consume(String clientFingerprint);
}
