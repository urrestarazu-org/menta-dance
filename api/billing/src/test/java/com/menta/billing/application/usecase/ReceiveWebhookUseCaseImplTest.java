package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.ParsedSignature;
import com.menta.billing.application.dto.WebhookSignal;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.WebhookInboxAppender;
import com.menta.billing.application.port.out.WebhookSignatureVerifier;
import com.menta.billing.domain.exception.WebhookSignatureInvalidException;
import com.menta.billing.domain.exception.WebhookTimestampExpiredException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReceiveWebhookUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final String SECRET = "test-secret";

    private WebhookSignatureVerifier signatureVerifier;
    private WebhookInboxAppender inboxAppender;
    private Clock clock;
    private ReceiveWebhookUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        signatureVerifier = mock(WebhookSignatureVerifier.class);
        inboxAppender = mock(WebhookInboxAppender.class);
        clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        useCase = new ReceiveWebhookUseCaseImpl(signatureVerifier, inboxAppender, clock, SECRET);
    }

    private WebhookSignal signal() {
        return new WebhookSignal("data-1", "req-1", "ts=" + NOW.getEpochSecond() + ",v1=abc");
    }

    @Test
    void appends_to_the_inbox_when_signature_and_timestamp_are_valid() {
        when(signatureVerifier.parse(any())).thenReturn(new ParsedSignature(String.valueOf(NOW.getEpochSecond()), "abc"));
        when(signatureVerifier.isValid(eq("data-1"), eq("req-1"), any(), eq("abc"), eq(SECRET))).thenReturn(true);

        useCase.receive(signal());

        verify(inboxAppender).appendIfNew("data-1:req-1", "data-1", "req-1", NOW);
    }

    @Test
    void succeeds_silently_on_a_duplicate_notification() {
        when(signatureVerifier.parse(any())).thenReturn(new ParsedSignature(String.valueOf(NOW.getEpochSecond()), "abc"));
        when(signatureVerifier.isValid(any(), any(), any(), any(), any())).thenReturn(true);
        when(inboxAppender.appendIfNew(any(), any(), any(), any())).thenReturn(false);

        useCase.receive(signal());

        verify(inboxAppender).appendIfNew(any(), any(), any(), any());
    }

    @Test
    void rejects_an_invalid_signature_without_touching_the_inbox() {
        when(signatureVerifier.parse(any())).thenReturn(new ParsedSignature(String.valueOf(NOW.getEpochSecond()), "abc"));
        when(signatureVerifier.isValid(any(), any(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.receive(signal())).isInstanceOf(WebhookSignatureInvalidException.class);
        verify(inboxAppender, never()).appendIfNew(any(), any(), any(), any());
    }

    @Test
    void rejects_a_timestamp_older_than_five_minutes() {
        Instant staleTimestamp = NOW.minusSeconds(301);
        when(signatureVerifier.parse(any()))
            .thenReturn(new ParsedSignature(String.valueOf(staleTimestamp.getEpochSecond()), "abc"));
        when(signatureVerifier.isValid(any(), any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> useCase.receive(signal())).isInstanceOf(WebhookTimestampExpiredException.class);
        verify(inboxAppender, never()).appendIfNew(any(), any(), any(), any());
    }

    @Test
    void rejects_a_malformed_timestamp_as_an_invalid_signature() {
        when(signatureVerifier.parse(any())).thenReturn(new ParsedSignature("not-a-number", "abc"));
        when(signatureVerifier.isValid(any(), any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> useCase.receive(signal())).isInstanceOf(WebhookSignatureInvalidException.class);
    }

    @Test
    void a_timestamp_exactly_at_the_five_minute_boundary_is_still_valid() {
        Instant boundaryTimestamp = NOW.minusSeconds(300);
        when(signatureVerifier.parse(any()))
            .thenReturn(new ParsedSignature(String.valueOf(boundaryTimestamp.getEpochSecond()), "abc"));
        when(signatureVerifier.isValid(any(), any(), any(), any(), any())).thenReturn(true);

        useCase.receive(signal());

        verify(inboxAppender).appendIfNew(any(), any(), any(), any());
    }
}
