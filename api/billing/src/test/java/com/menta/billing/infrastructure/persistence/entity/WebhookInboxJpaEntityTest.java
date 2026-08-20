package com.menta.billing.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.infrastructure.webhook.WebhookInboxStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WebhookInboxJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters_and_setters() {
        Instant receivedAt = Instant.now();
        Instant nextAttemptAt = receivedAt.plusSeconds(30);
        Instant processedAt = receivedAt.plusSeconds(60);

        WebhookInboxJpaEntity entity = new WebhookInboxJpaEntity(
            "dedupe-1", "mp-1", "req-1", WebhookInboxStatus.RECEIVED,
            1, nextAttemptAt, "timeout", receivedAt, processedAt
        );

        assertThat(entity.getId()).isNull();
        assertThat(entity.getDedupeKey()).isEqualTo("dedupe-1");
        assertThat(entity.getProviderPaymentId()).isEqualTo("mp-1");
        assertThat(entity.getRequestId()).isEqualTo("req-1");
        assertThat(entity.getStatus()).isEqualTo(WebhookInboxStatus.RECEIVED);
        assertThat(entity.getAttemptCount()).isEqualTo(1);
        assertThat(entity.getNextAttemptAt()).isEqualTo(nextAttemptAt);
        assertThat(entity.getLastError()).isEqualTo("timeout");
        assertThat(entity.getReceivedAt()).isEqualTo(receivedAt);
        assertThat(entity.getProcessedAt()).isEqualTo(processedAt);

        Instant newNextAttemptAt = receivedAt.plusSeconds(120);
        Instant newProcessedAt = receivedAt.plusSeconds(180);
        entity.setStatus(WebhookInboxStatus.PROCESSED);
        entity.setAttemptCount(2);
        entity.setNextAttemptAt(newNextAttemptAt);
        entity.setLastError("still failing");
        entity.setProcessedAt(newProcessedAt);

        assertThat(entity.getStatus()).isEqualTo(WebhookInboxStatus.PROCESSED);
        assertThat(entity.getAttemptCount()).isEqualTo(2);
        assertThat(entity.getNextAttemptAt()).isEqualTo(newNextAttemptAt);
        assertThat(entity.getLastError()).isEqualTo("still failing");
        assertThat(entity.getProcessedAt()).isEqualTo(newProcessedAt);
    }
}
