package com.menta.billing.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;

/**
 * A payment-proof access token failed validation (#33, US-BILLING-005, design C2/C5/C6).
 *
 * <p>Thrown for every rejection cause — missing signature, tampered payload/MAC, malformed shape,
 * unknown version, non-UUID payment id, expired, or a token that decodes cleanly but is bound to a
 * different {@code paymentId} than the one being requested. Deliberately a single exception type
 * with a single message: the anti-oracle property design C2 documents requires that "expired"
 * never be distinguishable from "never valid" by the caller. Maps to {@code 403} — the one
 * exception is absent-token, handled by {@code PaymentProofController} before this type is ever
 * thrown (design's deviation from a uniform response, spec-mandated {@code 401}).</p>
 */
public class InvalidProofAccessTokenException extends BusinessException {

    private static final String ERROR_CODE = "INVALID_PROOF_TOKEN";

    public InvalidProofAccessTokenException() {
        super(ERROR_CODE, "Payment proof access token is invalid or expired");
    }
}
