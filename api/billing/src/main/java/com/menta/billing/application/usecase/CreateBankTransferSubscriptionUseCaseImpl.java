package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.BankAccountDetails;
import com.menta.billing.application.dto.BankTransferInstructions;
import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.dto.RateLimitDecision;
import com.menta.billing.application.dto.SubscriptionCheckoutResult;
import com.menta.billing.application.port.in.CreateBankTransferSubscriptionUseCase;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.exception.BankTransferRateLimitedException;
import com.menta.billing.domain.exception.PaymentMethodNotAcceptedException;
import com.menta.billing.domain.exception.PlanNotAvailableException;
import com.menta.billing.domain.exception.SubscriptionAlreadyActiveException;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.Subscription;
import java.time.Instant;
import java.util.UUID;

/**
 * Opens a bank-transfer subscription (US-BILLING-003 escenario 1, design D3).
 *
 * <p>Unlike {@link CreateSubscriptionCheckoutUseCaseImpl}, this use case never calls a payment
 * provider: the buyer transfers manually, so the {@code Payment} is born directly in {@code
 * AwaitingManualVerification} with the configured bank account's CBU as its {@code
 * expectedMerchantAccountId} (design C2) — see {@link Payment#awaitingManualVerification} for why
 * that is a safety property, not filler.</p>
 *
 * <p>The daily creation budget (design C7) is consumed <strong>before</strong> the plan lookup or
 * any write, since attempting a checkout is itself the cost the budget bounds — matching {@code
 * RedisBillingPlansRateLimitPort}'s "every request counts" discipline rather than the
 * post-validation consumption used for proof uploads (P3c).</p>
 */
public class CreateBankTransferSubscriptionUseCaseImpl implements CreateBankTransferSubscriptionUseCase {

    /** Same value {@link CreateSubscriptionCheckoutUseCaseImpl} uses — one correlation scheme for the module. */
    private static final String EXTERNAL_REFERENCE_PREFIX = "SUB-";

    private final PlanRepository planRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BankTransferRateLimitPort rateLimitPort;
    private final Clock clock;
    private final BankAccountDetails bankAccountDetails;

    public CreateBankTransferSubscriptionUseCaseImpl(
        PlanRepository planRepository, PaymentRepository paymentRepository,
        SubscriptionRepository subscriptionRepository, BankTransferRateLimitPort rateLimitPort, Clock clock,
        BankAccountDetails bankAccountDetails
    ) {
        this.planRepository = planRepository;
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.rateLimitPort = rateLimitPort;
        this.clock = clock;
        this.bankAccountDetails = bankAccountDetails;
    }

    @Override
    public SubscriptionCheckoutResult create(CreateSubscriptionCheckoutCommand command) {
        RateLimitDecision decision = rateLimitPort.consumeSubscriptionCreation(command.userId());
        if (!decision.isAllowed()) {
            throw new BankTransferRateLimitedException(decision.getRetryAfter());
        }

        Plan plan = planRepository.findActiveById(PlanId.of(command.planId()))
            .orElseThrow(PlanNotAvailableException::new);
        if (!plan.accepts(command.paymentMethod())) {
            throw new PaymentMethodNotAcceptedException(command.paymentMethod(), plan.getPaymentMethods());
        }

        subscriptionRepository.findCurrentByUserId(command.userId()).ifPresent(current -> {
            throw new SubscriptionAlreadyActiveException(current.getEndDate().orElse(null));
        });

        Instant now = clock.now();
        PaymentId paymentId = PaymentId.generate();
        String externalReference = externalReferenceFor(paymentId);
        paymentRepository.save(Payment.awaitingManualVerification(
            paymentId, command.userId(), plan.getPrice(), externalReference, bankAccountDetails.cbu(),
            new PaymentTarget.Virtual(plan.getId().toString()), now
        ));

        Subscription subscription = subscriptionRepository.saveNewCheckout(Subscription.pendingCheckout(
            UUID.randomUUID(), paymentId, command.userId(), plan.getId(), command.idempotencyKey(), now
        ));

        BankTransferInstructions instructions =
            BankTransferInstructions.of(bankAccountDetails, plan.getPrice(), externalReference);
        return SubscriptionCheckoutResult.fromBankTransfer(subscription, externalReference, instructions);
    }

    private static String externalReferenceFor(PaymentId paymentId) {
        return EXTERNAL_REFERENCE_PREFIX + paymentId;
    }
}
