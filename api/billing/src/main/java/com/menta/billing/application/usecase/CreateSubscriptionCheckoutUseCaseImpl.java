package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.dto.OverlapNotice;
import com.menta.billing.application.dto.PaymentPreferenceRequest;
import com.menta.billing.application.dto.PaymentPreferenceResult;
import com.menta.billing.application.dto.SubscriptionCheckoutResult;
import com.menta.billing.application.port.in.CreateSubscriptionCheckoutUseCase;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.exception.PaymentMethodNotAcceptedException;
import com.menta.billing.domain.exception.PaymentPreferenceUnavailableException;
import com.menta.billing.domain.exception.PlanNotAvailableException;
import com.menta.billing.domain.exception.SubscriptionAlreadyActiveException;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.Subscription;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Opens a subscription checkout (US-BILLING-010).
 *
 * <p>Write order is the security property, not an implementation detail: the
 * local {@code Payment} and {@code Subscription} are persisted <em>before</em>
 * the provider is asked for anything. A confirmation that arrives later
 * always has a local row with an expected amount, merchant and reference to
 * be matched against — which is precisely what US-BILLING-010 rules out for
 * pre-configured static payment links.</p>
 *
 * <p>The provider returns a preference and a redirect URL, never a {@code
 * payment.id}; see {@link Payment} for why the binding happens later.</p>
 */
public class CreateSubscriptionCheckoutUseCaseImpl implements CreateSubscriptionCheckoutUseCase {

    /**
     * Prefix for the external reference we hand the provider. Derived from
     * the {@code PaymentId} rather than randomly generated, so a replayed
     * request reconstructs the same value without another lookup, and so a
     * reference found in the provider's dashboard names the local row it
     * belongs to.
     */
    private static final String EXTERNAL_REFERENCE_PREFIX = "SUB-";

    private final PlanRepository planRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentPreferencePort paymentPreferencePort;
    private final Clock clock;
    private final String merchantAccountId;

    public CreateSubscriptionCheckoutUseCaseImpl(
        PlanRepository planRepository, PaymentRepository paymentRepository,
        SubscriptionRepository subscriptionRepository, PaymentPreferencePort paymentPreferencePort,
        Clock clock, String merchantAccountId
    ) {
        this.planRepository = planRepository;
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentPreferencePort = paymentPreferencePort;
        this.clock = clock;
        this.merchantAccountId = merchantAccountId;
    }

    @Override
    public SubscriptionCheckoutResult create(CreateSubscriptionCheckoutCommand command) {
        if (command.paymentMethod() != PaymentMethod.MERCADO_PAGO) {
            // This use case owns Checkout Pro only. Bank transfer is a separate
            // flow (US-BILLING-003) and must never accidentally open an MP preference.
            throw new IllegalArgumentException("Checkout Pro requires MERCADO_PAGO");
        }

        Optional<Subscription> replay =
            subscriptionRepository.findByUserIdAndIdempotencyKey(command.userId(), command.idempotencyKey());
        if (replay.isPresent()) {
            // Escenario 5: same key, same answer, no second charge. Checked
            // before the already-active rule on purpose — a retry of the very
            // request that created the current subscription is not a conflict.
            return toResult(replay.get());
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
        paymentRepository.save(Payment.awaitingProvider(
            paymentId, command.userId(), plan.getPrice(), externalReference, merchantAccountId,
            new PaymentTarget.Virtual(plan.getId().toString()), now
        ));

        // The unique constraint behind saveNewCheckout — not the lookup above —
        // is what actually makes two simultaneous checkouts resolve to one
        // subscription. The loser rolls back with its Payment and never
        // reaches the provider.
        Subscription subscription = subscriptionRepository.saveNewCheckout(Subscription.pendingCheckout(
            UUID.randomUUID(), paymentId, command.userId(), plan.getId(), command.idempotencyKey(), now
        ));

        PaymentPreferenceResult preference = createPreference(plan, externalReference);
        return toResult(subscriptionRepository.save(
            subscription.withCheckout(preference.preferenceId(), preference.checkoutUrl())
        ));
    }

    private PaymentPreferenceResult createPreference(Plan plan, String externalReference) {
        try {
            return paymentPreferencePort.createPreference(
                new PaymentPreferenceRequest(externalReference, plan.getName(), plan.getPrice())
            );
        } catch (RuntimeException providerFailed) {
            throw new PaymentPreferenceUnavailableException(providerFailed);
        }
    }

    /**
     * Instance method, not {@code static}: both the replay branch and the new-checkout branch
     * fold through here, which is what makes computing the D3 overlap notice on only one of
     * them structurally impossible (A9) instead of a fact you must remember to keep true.
     */
    private SubscriptionCheckoutResult toResult(Subscription subscription) {
        return SubscriptionCheckoutResult.from(
            subscription, externalReferenceFor(subscription.getPaymentId().orElseThrow()), overlapNoticeFor(subscription)
        );
    }

    private OverlapNotice overlapNoticeFor(Subscription subscription) {
        return subscriptionRepository
            .findLatestCancelledWithRemainingAccess(subscription.getUserId(), subscription.getPlanId(), clock.now())
            .map(overlapping -> OverlapNotice.of(overlapping.getEndDate().orElseThrow()))
            .orElse(null);
    }

    private static String externalReferenceFor(PaymentId paymentId) {
        return EXTERNAL_REFERENCE_PREFIX + paymentId;
    }
}
