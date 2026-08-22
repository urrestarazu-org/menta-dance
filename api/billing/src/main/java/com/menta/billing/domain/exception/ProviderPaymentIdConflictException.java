package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * Two different provider transactions claim the same local {@code Payment}
 * (US-BILLING-002). Never resolved automatically and never overwrites the
 * existing binding: it ends in a manual reconciliation task.
 *
 * <p>Modelled as a dedicated exception rather than a boolean return so the
 * caller cannot silently treat "conflict" as "already bound, nothing to
 * do" — the two outcomes require opposite handling.</p>
 */
public class ProviderPaymentIdConflictException extends BusinessException {

    private static final String ERROR_CODE = "PROVIDER_PAYMENT_ID_CONFLICT";

    private final String boundProviderPaymentId;
    private final String conflictingProviderPaymentId;

    public ProviderPaymentIdConflictException(String boundProviderPaymentId, String conflictingProviderPaymentId) {
        super(
            ERROR_CODE,
            "Payment already bound to provider payment " + boundProviderPaymentId
                + "; refusing to rebind to " + conflictingProviderPaymentId
        );
        this.boundProviderPaymentId = boundProviderPaymentId;
        this.conflictingProviderPaymentId = conflictingProviderPaymentId;
    }

    public String getBoundProviderPaymentId() {
        return boundProviderPaymentId;
    }

    public String getConflictingProviderPaymentId() {
        return conflictingProviderPaymentId;
    }
}
