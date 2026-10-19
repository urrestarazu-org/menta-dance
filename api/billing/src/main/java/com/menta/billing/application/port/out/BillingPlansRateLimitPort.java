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

    /**
     * Consumes one unit of the caller's request budget and reports whether the request may proceed.
     *
     * <p>The budget is keyed by the opaque client fingerprint, and a limited decision carries the
     * time to wait.
     */
    RateLimitDecision consume(String clientFingerprint);
}
