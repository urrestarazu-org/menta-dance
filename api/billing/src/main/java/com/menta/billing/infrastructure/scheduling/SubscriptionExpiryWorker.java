package com.menta.billing.infrastructure.scheduling;

import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.model.Subscription;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expires one subscription inside its own {@code REQUIRES_NEW} transaction (US-BILLING-012,
 * design A6). A separate bean from {@link SubscriptionExpiryReconciler} — self-invocation would
 * bypass Spring's AOP proxy and run with no transaction, and {@code
 * SubscriptionRepositoryAdapter.save} is {@code Propagation.MANDATORY}.
 *
 * <p>{@code @Component}-scanned only (design A6b) — no {@code @Bean} for this class in {@code
 * BillingConfiguration}, mirroring {@code WebhookVerificationWorker}. Declaring a second
 * registration path would mean two instances racing over the same batch.</p>
 */
@Component
public class SubscriptionExpiryWorker {

    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public SubscriptionExpiryWorker(SubscriptionRepository subscriptionRepository, Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    /**
     * Re-reads the subscription <em>inside</em> this transaction — a fresh {@code @Version} and
     * the hydrated course snapshot. {@code save()} rewrites the snapshot from the aggregate it is
     * given, so saving a courseless read would silently delete it (A12's {@code replaceCourses}
     * trap). {@code expire()} no-ops (returns the same instance) whenever a concurrent actor has
     * already moved the row out of {@code ACTIVE}; that case is skipped without a write.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOne(UUID subscriptionId) {
        subscriptionRepository.findById(subscriptionId).ifPresent(current -> {
            Subscription expired = current.expire(clock.now());
            if (expired != current) {
                subscriptionRepository.save(expired);
            }
        });
    }
}
