package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException.Code;
import com.menta.bff.application.port.out.AdminBillingApiClient.ServiceUnavailableException;

/** What the manual payment verification use cases share: the checks of what can never be sent to the API. */
final class ManualPaymentSupport {

    /** The BFF's own limit for a reason or an evidence; the API sets none. */
    static final int MAX_TEXT_LENGTH = 500;

    private ManualPaymentSupport() {
    }

    /** A call without an access token cannot succeed: it reads as unavailable, without calling the API. */
    static String token(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ServiceUnavailableException("No access token for the Billing API");
        }
        return accessToken;
    }

    /** Only a canonical UUID reaches a path; anything else cannot be a payment. */
    static String paymentId(String paymentId) {
        String id = UuidFormat.normalize(paymentId);
        if (id == null) {
            throw new PaymentRefusalException(Code.PAYMENT_NOT_FOUND);
        }
        return id;
    }

    /** A reason or an evidence is required: trimmed, it must not be blank nor exceed {@link #MAX_TEXT_LENGTH}. */
    static String requiredText(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TEXT_LENGTH) {
            throw new PaymentRefusalException(Code.INVALID_REQUEST);
        }
        return trimmed;
    }
}
