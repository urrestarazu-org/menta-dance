package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.billing.application.port.out.BillingOutboxAppenderPort;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Reason;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * RED-GREEN: mirrors {@link PublishPhysicalPaymentCompletedUseCaseTest},
 * the producer side of the payment-level D10 event.
 *
 * <p>Spec/design scenarios exercised (design C1/C2 — the load-bearing
 * decision under test):
 * <ul>
 *   <li>"Payment-level append is keyed on PaymentId alone" — no
 *       {@code Purchase} lookup, unlike D1.</li>
 *   <li>"A null userId is serialized without failing" (site 130,
 *       {@code payment == null}).</li>
 *   <li>"JSON failure throws IllegalStateException without appending".</li>
 *   <li>"REQUIRES_NEW, never REQUIRED" — C2's whole point: this is the
 *       only propagation that lets the notification survive when the
 *       caller's ambient transaction (the doomed {@code markException}
 *       call) rolls back.</li>
 * </ul>
 */
class PublishPaymentFulfillmentFailedUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-08-24T13:00:00Z");
    private static final PaymentId PAYMENT_ID = PaymentId.generate();
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private BillingOutboxAppenderPort outboxAppender;
    private Clock clock;
    private PublishPaymentFulfillmentFailedUseCase useCase;

    @BeforeEach
    void setUp() {
        outboxAppender = mock(BillingOutboxAppenderPort.class);
        clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        useCase = new PublishPaymentFulfillmentFailedUseCase(outboxAppender, clock);
    }

    @Nested
    @DisplayName("Design C2: propagation MUST be REQUIRES_NEW, never REQUIRED")
    class PropagationIsRequiresNew {

        @Test
        void publish_method_is_annotated_REQUIRES_NEW() throws NoSuchMethodException {
            Method publish = PublishPaymentFulfillmentFailedUseCase.class.getMethod(
                "publish", PaymentId.class, UUID.class, Reason.class
            );
            Transactional annotation = findTransactional(publish);

            assertThat(annotation).isNotNull();
            assertThat(annotation.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        }

        private Transactional findTransactional(Method method) {
            for (Annotation annotation : method.getAnnotations()) {
                if (annotation instanceof Transactional transactional) {
                    return transactional;
                }
            }
            return null;
        }
    }

    @Nested
    @DisplayName("Spec scenario: append is keyed on PaymentId alone, no Purchase lookup")
    class AppendsOnPaymentIdAlone {

        @Test
        void appends_exactly_once_with_the_payment_scoped_event_type() {
            useCase.publish(PAYMENT_ID, USER_ID, Reason.TARGET_NOT_SCHEDULED);

            ArgumentCaptor<String> eventType = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> aggregateId = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
            verify(outboxAppender, times(1))
                .append(eventType.capture(), aggregateId.capture(), payload.capture());

            assertThat(eventType.getValue()).isEqualTo(BillingOutboxEventTypes.PAYMENT_FULFILLMENT_FAILED);
            assertThat(aggregateId.getValue()).isEqualTo(PAYMENT_ID.getValue().toString());
            assertThat(payload.getValue())
                .contains("\"paymentId\":\"" + PAYMENT_ID.getValue() + "\"")
                .contains("\"userId\":\"" + USER_ID + "\"")
                .contains("\"reason\":\"TARGET_NOT_SCHEDULED\"");
        }
    }

    @Nested
    @DisplayName("Spec scenario: a null userId (site 130, payment == null) is serialized without failing")
    class NullUserId {

        @Test
        void serializes_a_null_userId_without_throwing() {
            useCase.publish(PAYMENT_ID, null, Reason.TARGET_NOT_SCHEDULED);

            ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
            verify(outboxAppender, times(1)).append(any(), any(), payload.capture());

            assertThat(payload.getValue())
                .contains("\"userId\":null")
                .contains("\"paymentId\":\"" + PAYMENT_ID.getValue() + "\"");
        }
    }

    @Nested
    @DisplayName("Spec scenario: JSON failure throws IllegalStateException without appending")
    class SerializationFailure {

        @Test
        void fails_without_appending_when_payload_serialization_fails() throws Exception {
            ObjectWriter failingWriter = mock(ObjectWriter.class);
            when(failingWriter.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialization failed") { });
            useCase = new PublishPaymentFulfillmentFailedUseCase(outboxAppender, clock, failingWriter);

            assertThatThrownBy(() -> useCase.publish(PAYMENT_ID, USER_ID, Reason.TARGET_NOT_SCHEDULED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PaymentFulfillmentFailedOutboxPayload JSON serialization failed");

            verify(outboxAppender, never()).append(any(), any(), any());
        }
    }
}
