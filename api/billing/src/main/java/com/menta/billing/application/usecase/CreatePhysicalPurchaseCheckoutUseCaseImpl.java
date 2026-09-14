package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.CreatePhysicalPurchaseCheckoutCommand;
import com.menta.billing.application.dto.PaymentPreferenceRequest;
import com.menta.billing.application.dto.PaymentPreferenceResult;
import com.menta.billing.application.dto.PhysicalPurchaseCheckoutResult;
import com.menta.billing.application.dto.ScheduledSessionSnapshot;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PhysicalCourseAvailabilityPort;
import com.menta.billing.application.port.in.CreatePhysicalPurchaseCheckoutUseCase;
import com.menta.billing.application.port.out.PhysicalCourseQuoteRepository;
import com.menta.billing.domain.exception.PaymentPreferenceUnavailableException;
import com.menta.billing.domain.exception.PhysicalCapacityUnavailableException;
import com.menta.billing.domain.exception.PhysicalCourseQuoteExpiredException;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.PhysicalCourseQuote;
import com.menta.billing.domain.model.PurchaseType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Opens a physical-course purchase checkout (#41, US-PHYSICAL-004, design
 * A6/A7).
 *
 * <p>Mirrors {@link CreateSubscriptionCheckoutUseCaseImpl}'s write-order
 * discipline: the local {@code Payment} is persisted <em>before</em> the
 * provider is asked for anything. Unlike the subscription flow, a physical
 * purchase creates no second local aggregate at checkout time — {@code
 * Purchase} is created later, from the confirmed webhook (see {@code
 * api:app}'s outbox handler) — so replay idempotency cannot key on a second
 * row the way {@code Subscription.findByUserIdAndIdempotencyKey} does.
 * Instead, {@link #externalReferenceFor} deterministically derives the same
 * {@code expectedExternalReference} from {@code (userId, idempotencyKey)}
 * every time, and {@link PaymentRepository#findByExternalReference} (already
 * unique, US-BILLING-010) is the whole idempotency mechanism: a replay never
 * writes a second {@code Payment} row. Because that provider preference data
 * is not persisted anywhere on {@code Payment}, a replay still asks the
 * provider for a checkout link — never a second charge, since {@code
 * createPreference} only opens a redirect, it does not move money — this is
 * a deliberate scope trade-off against extending {@code billing_payments}'
 * schema, which is out of this PR's reach (Flyway migrations live in
 * {@code api:app}).</p>
 *
 * <p>Order of checks matters (design A7): quote validity is resolved before
 * capacity availability, so an expired quote is never described by a
 * capacity reading that no longer applies.</p>
 */
public class CreatePhysicalPurchaseCheckoutUseCaseImpl implements CreatePhysicalPurchaseCheckoutUseCase {

    /**
     * Generic, business-facing (Spanish) title sent to the payment provider.
     * Unlike the subscription flow, there is no per-plan name to forward —
     * the quote itself carries no human-readable title, only a {@code
     * courseId} reference (billing never resolves Physical's course names,
     * per this module's independence from {@code api:physical}).
     */
    private static final String CHECKOUT_TITLE = "Compra de curso presencial";
    private static final String EXTERNAL_REFERENCE_PREFIX = "PHY-";

    private final PhysicalCourseQuoteRepository quoteRepository;
    private final PaymentRepository paymentRepository;
    private final PhysicalCourseAvailabilityPort availabilityPort;
    private final PaymentPreferencePort paymentPreferencePort;
    private final Clock clock;
    private final String merchantAccountId;

    public CreatePhysicalPurchaseCheckoutUseCaseImpl(
        PhysicalCourseQuoteRepository quoteRepository, PaymentRepository paymentRepository,
        PhysicalCourseAvailabilityPort availabilityPort, PaymentPreferencePort paymentPreferencePort, Clock clock,
        String merchantAccountId
    ) {
        this.quoteRepository = quoteRepository;
        this.paymentRepository = paymentRepository;
        this.availabilityPort = availabilityPort;
        this.paymentPreferencePort = paymentPreferencePort;
        this.clock = clock;
        this.merchantAccountId = merchantAccountId;
    }

    @Override
    public PhysicalPurchaseCheckoutResult create(CreatePhysicalPurchaseCheckoutCommand command) {
        if (command.paymentMethod() != PaymentMethod.MERCADO_PAGO) {
            // Same rationale as CreateSubscriptionCheckoutUseCaseImpl: Checkout Pro
            // owns MERCADO_PAGO only; bank transfer is a separate, unbuilt flow here.
            throw new IllegalArgumentException("Checkout Pro requires MERCADO_PAGO");
        }

        String externalReference = externalReferenceFor(command.userId(), command.idempotencyKey());
        Optional<Payment> replay = paymentRepository.findByExternalReference(externalReference);
        if (replay.isPresent()) {
            // Same key, same answer, no second Payment — the deterministic external
            // reference above is what makes this replay findable without a persisted
            // idempotency key column.
            return toResult(replay.get());
        }

        Instant now = clock.now();
        PhysicalCourseQuote quote = quoteRepository.findById(command.quoteId())
            .filter(candidate -> candidate.getExpiresAt().isAfter(now))
            .orElseThrow(PhysicalCourseQuoteExpiredException::new);

        // A7: validity is checked before availability, and 410 for an expired
        // quote is thrown before this planner call ever runs.
        ensureCoverageIsAvailable(quote, now);

        PaymentId paymentId = PaymentId.generate();
        Payment payment = paymentRepository.save(Payment.awaitingProvider(
            paymentId, command.userId(), quote.getAmount(), externalReference, merchantAccountId,
            new PaymentTarget.Physical(quote.getId().toString()), now
        ));

        return toResult(payment);
    }

    /**
     * A6/D5: best-effort, read-time-only check — reuses the same {@link
     * CoveragePlanner} the confirmation-time outbox handler runs, but with
     * {@code clock.now()} instead of {@code confirmedAt} and {@code
     * requireAvailable = true}. Never reserves anything; see {@link
     * PhysicalCapacityUnavailableException}'s javadoc for what this
     * deliberately does not promise.
     */
    private void ensureCoverageIsAvailable(PhysicalCourseQuote quote, Instant now) {
        // INDIVIDUAL's selectedSessionId was picked at quote time and may sit
        // before `now` for a same-day class — same periodStart reasoning the
        // outbox handler already applies for the identical purchaseType split.
        Instant periodStart = quote.getPurchaseType() == PurchaseType.INDIVIDUAL ? Instant.EPOCH : now;
        List<ScheduledSessionSnapshot> scheduledSessions = availabilityPort.findScheduledSessions(
            quote.getCourseId(), periodStart, now.plus(CoveragePlanner.COVERAGE_LOOKAHEAD)
        );

        CoveragePlanner.Plan plan = switch (quote.getPurchaseType()) {
            case MONTHLY -> CoveragePlanner.planMonthly(scheduledSessions, now, quote.getScheduledSessionCount(), true);
            case INDIVIDUAL -> CoveragePlanner.planIndividual(scheduledSessions, quote.getSelectedSessionId(), true);
        };

        if (!(plan instanceof CoveragePlanner.Plan.Complete)) {
            throw new PhysicalCapacityUnavailableException();
        }
    }

    private PhysicalPurchaseCheckoutResult toResult(Payment payment) {
        return PhysicalPurchaseCheckoutResult.from(payment, createPreference(payment));
    }

    private PaymentPreferenceResult createPreference(Payment payment) {
        try {
            return paymentPreferencePort.createPreference(new PaymentPreferenceRequest(
                payment.getExpectedExternalReference(), CHECKOUT_TITLE, payment.getExpectedAmount()
            ));
        } catch (RuntimeException providerFailed) {
            throw new PaymentPreferenceUnavailableException(providerFailed);
        }
    }

    /**
     * Deterministic, not random (design deviation, see class javadoc): the
     * same {@code (userId, idempotencyKey)} pair always yields the same
     * reference, which is what lets {@link PaymentRepository#findByExternalReference}
     * double as the idempotency lookup with zero new persistence.
     */
    private static String externalReferenceFor(UUID userId, String idempotencyKey) {
        String seed = userId + ":" + idempotencyKey;
        return EXTERNAL_REFERENCE_PREFIX
            + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
