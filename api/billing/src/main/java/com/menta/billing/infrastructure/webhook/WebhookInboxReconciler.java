package com.menta.billing.infrastructure.webhook;

import com.menta.billing.infrastructure.persistence.entity.WebhookInboxJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.WebhookInboxJpaRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls {@code billing_webhook_inbox} for eligible rows and dispatches each
 * to {@link WebhookVerificationWorker} (ADR-0038). Lives in {@code
 * api:billing}, not {@code api:app} — unlike auth's outbox, this never
 * composes ports across modules, so there is no reason to route it through
 * the composition root.
 *
 * <p>{@code @ConditionalOnProperty} mirrors auth's {@code OutboxBlacklistReconcilerTrigger} and
 * physical's {@code HoldExpiryReconciler}: {@code billing.webhook.reconcile-rate-ms} only
 * controls the interval, since {@code @Scheduled(fixedRate)} fires its first tick as soon as the
 * context is up regardless of how large the interval is — {@code
 * billing.webhook.reconcile.enabled} is the real off switch. No test drives this reconciler by
 * hand through Spring, so unlike those two the class needs no separate worker split to stay
 * unconditional.</p>
 */
@Component
@ConditionalOnProperty(
    name = "billing.webhook.reconcile.enabled", havingValue = "true", matchIfMissing = true
)
public class WebhookInboxReconciler {

    private final WebhookInboxJpaRepository inboxRepository;
    private final WebhookVerificationWorker worker;
    private final int batchSize;

    public WebhookInboxReconciler(
        WebhookInboxJpaRepository inboxRepository, WebhookVerificationWorker worker,
        @Value("${billing.webhook.reconcile-batch-size:20}") int batchSize
    ) {
        this.inboxRepository = inboxRepository;
        this.worker = worker;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedRateString = "${billing.webhook.reconcile-rate-ms:5000}")
    public void tick() {
        List<WebhookInboxJpaEntity> eligible =
            inboxRepository.findEligibleForProcessing(Instant.now(), PageRequest.of(0, batchSize));
        for (WebhookInboxJpaEntity row : eligible) {
            worker.process(row);
        }
    }
}
