package com.menta.billing.infrastructure.scheduling;

import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.usecase.PaymentFulfillmentService;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expires one bank-transfer payment inside its own {@code REQUIRES_NEW} transaction (#31,
 * US-BILLING-003, design C6). A separate bean from {@link PaymentExpiryReconciler} — mirrors
 * {@code SubscriptionExpiryWorker}/{@code SubscriptionExpiryReconciler} exactly: self-invocation
 * would bypass Spring's AOP proxy and run with no transaction, and {@code
 * PaymentRepositoryAdapter.save} is {@code Propagation.MANDATORY}.
 *
 * <p>{@code @Component}-scanned only — no {@code @Bean} for this class in {@code
 * BillingConfiguration}, the same shape {@code SubscriptionExpiryWorker} and {@code
 * WebhookVerificationWorker} use. Declaring a second registration path would mean two instances
 * racing over the same batch.</p>
 */
@Component
public class PaymentExpiryWorker {

    private final PaymentRepository paymentRepository;
    private final PaymentFulfillmentService paymentFulfillmentService;
    private final Clock clock;

    public PaymentExpiryWorker(
        PaymentRepository paymentRepository, PaymentFulfillmentService paymentFulfillmentService, Clock clock
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentFulfillmentService = paymentFulfillmentService;
        this.clock = clock;
    }

    /**
     * Re-reads the payment <em>inside</em> this transaction. {@code
     * expireAwaitingManualVerification} no-ops (returns the same instance) whenever a concurrent
     * admin decision or an earlier sweep tick already moved the row out of {@code
     * AwaitingManualVerification}; that case is skipped without a write. On a real transition,
     * {@code save} and {@link PaymentFulfillmentService#release} run inside this same
     * transaction, so both the {@code Payment} and its {@code Subscription} commit together.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOne(UUID paymentId) {
        paymentRepository.findById(PaymentId.of(paymentId)).ifPresent(current -> {
            Payment expired = current.expireAwaitingManualVerification(clock.now());
            if (expired != current) {
                paymentRepository.save(expired);
                paymentFulfillmentService.release(expired);
            }
        });
    }
}
