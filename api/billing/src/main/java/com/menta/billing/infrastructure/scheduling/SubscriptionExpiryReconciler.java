package com.menta.billing.infrastructure.scheduling;

import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.SubscriptionRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls for {@code ACTIVE} subscriptions whose {@code endDate} has already passed and dispatches
 * each id to {@link SubscriptionExpiryWorker} (US-BILLING-012, design A6). Mirrors {@code
 * WebhookInboxReconciler} exactly: this method carries <strong>no</strong> {@code
 * @Transactional} and delegates each row to a separate bean — Spring's AOP proxy never
 * intercepts a self-invoked call, so {@code this.expireOne(...)} inside this class would run
 * with no transaction at all, and {@code SubscriptionRepositoryAdapter.save} is {@code
 * Propagation.MANDATORY}. A row that fails is logged and skipped; {@code expire()} is
 * idempotent, so the next tick retries it.
 *
 * <p>{@code @ConditionalOnProperty} sits on this CLASS, not on {@link #tick()} — Spring only
 * evaluates that condition on a component/configuration class or on a {@code @Bean} method; on a
 * plain method such as {@code tick()} it would be inert and the job would run unconditionally
 * (design A16). {@code billing.subscription.expiry.rate-ms} only controls the interval; this
 * property is the real off switch.</p>
 */
@Component
@ConditionalOnProperty(
    name = "billing.subscription.expiry.enabled", havingValue = "true", matchIfMissing = true
)
public class SubscriptionExpiryReconciler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryReconciler.class);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionExpiryWorker worker;
    private final Clock clock;
    private final int batchSize;

    public SubscriptionExpiryReconciler(
        SubscriptionRepository subscriptionRepository, SubscriptionExpiryWorker worker, Clock clock,
        @Value("${billing.subscription.expiry.batch-size:100}") int batchSize
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.worker = worker;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedRateString = "${billing.subscription.expiry.rate-ms:60000}")
    public void tick() {
        List<UUID> expirable = subscriptionRepository.findExpirableIds(clock.now(), batchSize);
        for (UUID subscriptionId : expirable) {
            try {
                worker.expireOne(subscriptionId);
            } catch (RuntimeException failed) {
                log.warn(
                    "Subscription expiry failed subscriptionId={} cause={}", subscriptionId, failed.getMessage()
                );
            }
        }
    }
}
