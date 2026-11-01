package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.shared.outbox.OutboxStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PurchaseResolvedOutboxEventHandlerTest {

    private static final UUID PAYMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PURCHASE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ADMIN_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-13T12:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final PurchaseResolvedOutboxEventHandler handler =
        new PurchaseResolvedOutboxEventHandler(objectMapper);
    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(PurchaseResolvedOutboxEventHandler.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void captureLogs() {
        appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        handlerLogger.detachAppender(appender);
    }

    @Test
    void supports_only_the_purchase_resolved_event_type() {
        assertThat(handler.supports(BillingOutboxEventTypes.PURCHASE_RESOLVED)).isTrue();
        assertThat(handler.supports(BillingOutboxEventTypes.PURCHASE_EXCEPTIONED)).isFalse();
        assertThat(handler.supports("something.else")).isFalse();
    }

    @Test
    void logs_the_resolution_at_info_without_any_free_text() {
        String payload = "{\"paymentId\":\"" + PAYMENT_ID + "\",\"purchaseId\":\"" + PURCHASE_ID
            + "\",\"userId\":\"" + USER_ID + "\",\"resolution\":\"REFUNDED\",\"resolvedBy\":\"" + ADMIN_ID
            + "\",\"occurredAt\":\"" + OCCURRED_AT + "\"}";

        handler.handle(row(payload));

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
            .contains(PURCHASE_ID.toString(), PAYMENT_ID.toString(), "REFUNDED", ADMIN_ID.toString());
    }

    @Test
    void fails_on_an_unreadable_payload_so_the_worker_marks_the_row_failed() {
        assertThatThrownBy(() -> handler.handle(row("not json")))
            .isInstanceOf(OutboxDispatchException.class)
            .hasMessageContaining(BillingOutboxEventTypes.PURCHASE_RESOLVED);
    }

    private static OutboxRowJpaEntity row(String payload) {
        return new OutboxRowJpaEntity(
            "01H9X3F4Z9YJ7K5Q6T2R8V1N4P", BillingOutboxEventTypes.PURCHASE_RESOLVED, PAYMENT_ID.toString(),
            payload, OutboxStatus.PENDING, 0, null, null, Instant.parse("2026-10-13T12:00:00Z"), null
        );
    }
}
