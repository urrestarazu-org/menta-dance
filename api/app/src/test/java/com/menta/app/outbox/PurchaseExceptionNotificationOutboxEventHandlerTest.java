package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.billing.application.dto.PurchaseExceptionNotification;
import com.menta.billing.application.port.out.PurchaseExceptionNotificationPort;
import com.menta.shared.outbox.OutboxStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseExceptionNotificationOutboxEventHandlerTest {

    private static final UUID PAYMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PURCHASE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-17T12:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock private PurchaseExceptionNotificationPort notificationPort;

    @Test
    void supports_both_purchase_exception_event_types_and_only_those() {
        PurchaseExceptionNotificationOutboxEventHandler handler =
            new PurchaseExceptionNotificationOutboxEventHandler(notificationPort, objectMapper);

        assertThat(handler.supports(BillingOutboxEventTypes.PURCHASE_EXCEPTIONED)).isTrue();
        assertThat(handler.supports(BillingOutboxEventTypes.PAYMENT_FULFILLMENT_FAILED)).isTrue();
        assertThat(handler.supports(BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED)).isFalse();
        assertThat(handler.supports("something.else")).isFalse();
    }

    @Test
    void maps_the_purchase_exceptioned_payload_with_a_purchaseId() {
        PurchaseExceptionNotificationOutboxEventHandler handler =
            new PurchaseExceptionNotificationOutboxEventHandler(notificationPort, objectMapper);
        String payload = "{\"paymentId\":\"" + PAYMENT_ID + "\",\"purchaseId\":\"" + PURCHASE_ID
            + "\",\"userId\":\"" + USER_ID + "\",\"reason\":\"CAPACITY_BELOW_ASSIGNED\",\"occurredAt\":\""
            + OCCURRED_AT + "\"}";
        OutboxRowJpaEntity row = rowWithPayload(BillingOutboxEventTypes.PURCHASE_EXCEPTIONED, payload);

        handler.handle(row);

        ArgumentCaptor<PurchaseExceptionNotification> captor = ArgumentCaptor.forClass(PurchaseExceptionNotification.class);
        verify(notificationPort).notify(captor.capture());
        PurchaseExceptionNotification notification = captor.getValue();
        assertThat(notification.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(notification.purchaseId()).isEqualTo(PURCHASE_ID);
        assertThat(notification.userId()).isEqualTo(USER_ID);
        assertThat(notification.reason()).isEqualTo("CAPACITY_BELOW_ASSIGNED");
        assertThat(notification.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(notification.eventType()).isEqualTo(BillingOutboxEventTypes.PURCHASE_EXCEPTIONED);
    }

    @Test
    void maps_the_payment_fulfillment_failed_payload_without_a_purchaseId() {
        PurchaseExceptionNotificationOutboxEventHandler handler =
            new PurchaseExceptionNotificationOutboxEventHandler(notificationPort, objectMapper);
        String payload = "{\"paymentId\":\"" + PAYMENT_ID + "\",\"userId\":null,\"reason\":\"TARGET_NOT_SCHEDULED\""
            + ",\"occurredAt\":\"" + OCCURRED_AT + "\"}";
        OutboxRowJpaEntity row = rowWithPayload(BillingOutboxEventTypes.PAYMENT_FULFILLMENT_FAILED, payload);

        handler.handle(row);

        ArgumentCaptor<PurchaseExceptionNotification> captor = ArgumentCaptor.forClass(PurchaseExceptionNotification.class);
        verify(notificationPort).notify(captor.capture());
        PurchaseExceptionNotification notification = captor.getValue();
        assertThat(notification.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(notification.purchaseId()).isNull();
        assertThat(notification.userId()).isNull();
        assertThat(notification.reason()).isEqualTo("TARGET_NOT_SCHEDULED");
        assertThat(notification.eventType()).isEqualTo(BillingOutboxEventTypes.PAYMENT_FULFILLMENT_FAILED);
    }

    @Test
    void propagates_send_failures_so_the_worker_marks_the_row_failed() {
        PurchaseExceptionNotificationOutboxEventHandler handler =
            new PurchaseExceptionNotificationOutboxEventHandler(notificationPort, objectMapper);
        String payload = "{\"paymentId\":\"" + PAYMENT_ID + "\",\"purchaseId\":\"" + PURCHASE_ID
            + "\",\"userId\":\"" + USER_ID + "\",\"reason\":\"CAPACITY_BELOW_ASSIGNED\",\"occurredAt\":\""
            + OCCURRED_AT + "\"}";
        OutboxRowJpaEntity row = rowWithPayload(BillingOutboxEventTypes.PURCHASE_EXCEPTIONED, payload);
        RuntimeException smtpDown = new RuntimeException("smtp unavailable");
        doThrow(smtpDown).when(notificationPort).notify(any(PurchaseExceptionNotification.class));

        assertThatThrownBy(() -> handler.handle(row)).isSameAs(smtpDown);
    }

    private static OutboxRowJpaEntity rowWithPayload(String eventType, String payload) {
        return new OutboxRowJpaEntity(
            "01H9X3F4Z9YJ7K5Q6T2R8V1N4P", eventType, PAYMENT_ID.toString(), payload,
            OutboxStatus.PENDING, 0, null, null, Instant.parse("2026-08-15T12:00:00Z"), null
        );
    }
}
