package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.ParsedSignature;
import com.menta.billing.application.dto.WebhookSignal;
import com.menta.billing.application.port.in.ReceiveWebhookUseCase;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.WebhookInboxAppender;
import com.menta.billing.application.port.out.WebhookSignatureVerifier;
import com.menta.billing.domain.exception.WebhookSignatureInvalidException;
import com.menta.billing.domain.exception.WebhookTimestampExpiredException;
import java.time.Duration;
import java.time.Instant;

public class ReceiveWebhookUseCaseImpl implements ReceiveWebhookUseCase {

    private static final Duration MAX_TIMESTAMP_AGE = Duration.ofMinutes(5);

    private final WebhookSignatureVerifier signatureVerifier;
    private final WebhookInboxAppender inboxAppender;
    private final Clock clock;
    private final String hmacSecret;

    public ReceiveWebhookUseCaseImpl(
        WebhookSignatureVerifier signatureVerifier, WebhookInboxAppender inboxAppender, Clock clock,
        String hmacSecret
    ) {
        this.signatureVerifier = signatureVerifier;
        this.inboxAppender = inboxAppender;
        this.clock = clock;
        this.hmacSecret = hmacSecret;
    }

    @Override
    public void receive(WebhookSignal signal) {
        ParsedSignature parsed = signatureVerifier.parse(signal.signatureHeader());
        if (!signatureVerifier.isValid(
            signal.dataId(), signal.requestId(), parsed.timestamp(), parsed.hash(), hmacSecret
        )) {
            throw new WebhookSignatureInvalidException();
        }

        Instant timestamp = parseEpochSeconds(parsed.timestamp());
        Instant now = clock.now();
        if (timestamp.isBefore(now.minus(MAX_TIMESTAMP_AGE))) {
            throw new WebhookTimestampExpiredException();
        }

        String dedupeKey = signal.dataId() + ":" + signal.requestId();
        inboxAppender.appendIfNew(dedupeKey, signal.dataId(), signal.requestId(), now);
    }

    private static Instant parseEpochSeconds(String timestamp) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(timestamp));
        } catch (NumberFormatException malformedTimestamp) {
            throw new WebhookSignatureInvalidException();
        }
    }
}
