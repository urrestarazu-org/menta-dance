package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException.Code;
import com.menta.bff.application.port.out.AdminBillingApiClient.ServiceUnavailableException;

/** What the manual payment verification use cases share: the checks of what can never be sent to the API. */
final class ManualPaymentSupport {

    /** The BFF's own limit for a reason or an evidence; the API sets none. */
    static final int MAX_TEXT_LENGTH = 500;

    /** Page size when the caller names none. */
    static final int DEFAULT_SIZE = 20;

    /** Largest page the API accepts: a bigger request is cut here, because the API would answer 400. */
    static final int MAX_SIZE = 50;

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
        return uuid(paymentId, Code.PAYMENT_NOT_FOUND);
    }

    /** Same for any other resource: the refusal is the "not found" code of that resource. */
    static String uuid(String id, Code notFound) {
        String normalized = UuidFormat.normalize(id);
        if (normalized == null) {
            throw new PaymentRefusalException(notFound);
        }
        return normalized;
    }

    /** Zero-based page index: {@code null} or negative reads the first page. */
    static int pageIndex(Integer page) {
        return page == null ? 0 : Math.max(page, 0);
    }

    /** Page size: {@code null} or below 1 reads {@link #DEFAULT_SIZE}, above {@link #MAX_SIZE} reads that. */
    static int pageSize(Integer size) {
        return size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
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
