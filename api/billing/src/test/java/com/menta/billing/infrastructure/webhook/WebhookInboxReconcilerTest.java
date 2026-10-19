package com.menta.billing.infrastructure.webhook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.infrastructure.persistence.entity.WebhookInboxJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.WebhookInboxJpaRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class WebhookInboxReconcilerTest {

    @Test
    void dispatches_every_eligible_row_to_the_worker() {
        WebhookInboxJpaRepository inboxRepository = mock(WebhookInboxJpaRepository.class);
        WebhookVerificationWorker worker = mock(WebhookVerificationWorker.class);
        WebhookInboxJpaEntity row1 = new WebhookInboxJpaEntity(
            "a:1", "mp-1", "1", WebhookInboxStatus.RECEIVED, 0, null, null, Instant.now(), null
        );
        WebhookInboxJpaEntity row2 = new WebhookInboxJpaEntity(
            "b:2", "mp-2", "2", WebhookInboxStatus.RECEIVED, 0, null, null, Instant.now(), null
        );
        when(inboxRepository.findEligibleForProcessing(any(Instant.class), any(Pageable.class)))
            .thenReturn(List.of(row1, row2));

        new WebhookInboxReconciler(inboxRepository, worker, 20).tick();

        verify(worker, times(1)).process(row1);
        verify(worker, times(1)).process(row2);
    }
}
