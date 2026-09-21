package com.menta.billing.infrastructure.scheduling;

import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls for bank-transfer payments still {@code AwaitingManualVerification} whose {@code
 * createdAt} is older than the configured expiry window and dispatches each id to {@link
 * PaymentExpiryWorker} (#31, US-BILLING-003, design C6). Mirrors {@code
 * SubscriptionExpiryReconciler} exactly: this method carries <strong>no</strong> {@code
 * @Transactional} and delegates each row to a separate bean — Spring's AOP proxy never intercepts
 * a self-invoked call, so {@code this.expireOne(...)} inside this class would run with no
 * transaction at all, and {@code PaymentRepositoryAdapter.save} is {@code
 * Propagation.MANDATORY}. A row that fails is logged and skipped; {@code
 * expireAwaitingManualVerification} is idempotent, so the next tick retries it.
 *
 * <p>{@code @ConditionalOnProperty} sits on this CLASS, not on {@link #tick()} — Spring only
 * evaluates that condition on a component/configuration class or on a {@code @Bean} method; on a
 * plain method such as {@code tick()} it would be inert and the job would run unconditionally.
 * {@code billing.bank-transfer.expiry.rate-ms} only controls the interval; this property is the
 * real off switch.</p>
 */
@Component
@ConditionalOnProperty(
    name = "billing.bank-transfer.expiry.enabled", havingValue = "true", matchIfMissing = true
)
public class PaymentExpiryReconciler {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpiryReconciler.class);

    private final PaymentRepository paymentRepository;
    private final PaymentExpiryWorker worker;
    private final Clock clock;
    private final int batchSize;
    private final Duration window;

    public PaymentExpiryReconciler(
        PaymentRepository paymentRepository, PaymentExpiryWorker worker, Clock clock,
        @Value("${billing.bank-transfer.expiry.batch-size:100}") int batchSize,
        @Value("${billing.bank-transfer.expiry.window-hours:72}") long windowHours
    ) {
        this.paymentRepository = paymentRepository;
        this.worker = worker;
        this.clock = clock;
        this.batchSize = batchSize;
        this.window = Duration.ofHours(windowHours);
    }

    @Scheduled(fixedRateString = "${billing.bank-transfer.expiry.rate-ms:60000}")
    public void tick() {
        List<UUID> expirable = paymentRepository.findExpirableBankTransferIds(clock.now().minus(window), batchSize);
        for (UUID paymentId : expirable) {
            try {
                worker.expireOne(paymentId);
            } catch (RuntimeException failed) {
                log.warn("Payment expiry failed paymentId={} cause={}", paymentId, failed.getMessage());
            }
        }
    }
}
