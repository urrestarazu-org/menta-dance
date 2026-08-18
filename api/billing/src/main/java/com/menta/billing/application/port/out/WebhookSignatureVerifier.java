package com.menta.billing.application.port.out;

import com.menta.billing.application.dto.ParsedSignature;
import com.menta.billing.domain.exception.WebhookSignatureInvalidException;

/**
 * HMAC-SHA256 verification for Mercado Pago's {@code x-signature} header
 * (US-BILLING-002). Timing-safe comparison is an implementation obligation,
 * not optional — see the real adapter's Javadoc for why.
 */
public interface WebhookSignatureVerifier {

    /** @throws WebhookSignatureInvalidException if the header is missing {@code ts} or {@code v1}. */
    ParsedSignature parse(String signatureHeader);

    /** Manifest is {@code id:{dataId};request-id:{requestId};ts:{timestamp};} — never the raw JSON body. */
    boolean isValid(String dataId, String requestId, String timestamp, String providedHash, String secret);
}
