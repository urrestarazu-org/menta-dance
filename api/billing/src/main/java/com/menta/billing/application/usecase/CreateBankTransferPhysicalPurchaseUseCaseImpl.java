package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.BankAccountDetails;
import com.menta.billing.application.dto.BankTransferInstructions;
import com.menta.billing.application.dto.CreatePhysicalPurchaseCheckoutCommand;
import com.menta.billing.application.dto.PhysicalPurchaseBankTransferCheckoutResult;
import com.menta.billing.application.dto.RateLimitDecision;
import com.menta.billing.application.port.in.CreateBankTransferPhysicalPurchaseUseCase;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PhysicalCourseAvailabilityPort;
import com.menta.billing.application.port.out.PhysicalCourseQuoteRepository;
import com.menta.billing.domain.exception.BankTransferRateLimitedException;
import com.menta.billing.domain.exception.PhysicalCourseQuoteExpiredException;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.PhysicalCourseQuote;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Opens a bank-transfer physical-course purchase (#36, US-BILLING-008, design C2).
 *
 * <p>Unlike {@link CreatePhysicalPurchaseCheckoutUseCaseImpl}'s Mercado Pago arm, this use case
 * never calls a payment provider and never reserves capacity (D1): the buyer transfers manually,
 * so the {@code Payment} is born directly in {@code AwaitingManualVerification} with the
 * configured bank account's CBU as its {@code expectedMerchantAccountId} — see {@link
 * Payment#awaitingManualVerification} for why that is a safety property, not filler. {@link
 * PhysicalCoverageAvailability#requireComplete} still runs as a best-effort, non-binding courtesy
 * (D7): its {@code Plan} is read and discarded, never followed by a {@code
 * PhysicalCapacityHoldPort#hold} call — the real capacity decision happens later, at approval,
 * through the already-shipped {@code HoldNotFound} branch (D1). This class deliberately has no
 * {@code PhysicalCapacityHoldPort} and no {@code PaymentPreferencePort} collaborator at all
 * (design C2): the absence is structural, not a per-call choice.</p>
 *
 * <p>The daily creation budget (design C2/C7, D4) is consumed <strong>before</strong> the replay
 * lookup, the quote lookup, or any write — attempting a checkout is itself the cost the budget
 * bounds, matching {@link CreateBankTransferSubscriptionUseCaseImpl}'s discipline. It is shared
 * with the bank-transfer subscription rail via {@link
 * BankTransferRateLimitPort#consumeBankTransferCreation}, so a replay still costs one unit (design
 * C1's idempotency consequence).</p>
 */
public class CreateBankTransferPhysicalPurchaseUseCaseImpl implements CreateBankTransferPhysicalPurchaseUseCase {

    /**
     * Distinct from {@link CreatePhysicalPurchaseCheckoutUseCaseImpl}'s {@code "PHY-"} (design
     * C1) — both physical arms would otherwise derive the same reference from the same {@code
     * (userId, idempotencyKey)} pair, letting a bank-transfer replay resolve an {@code
     * AwaitingProvider} payment whose {@code expectedMerchantAccountId} is the Mercado Pago
     * merchant id, not the CBU.
     */
    private static final String EXTERNAL_REFERENCE_PREFIX = "PHY-BT-";

    private final PhysicalCourseQuoteRepository quoteRepository;
    private final PaymentRepository paymentRepository;
    private final PhysicalCourseAvailabilityPort availabilityPort;
    private final BankTransferRateLimitPort rateLimitPort;
    private final Clock clock;
    private final BankAccountDetails bankAccountDetails;

    public CreateBankTransferPhysicalPurchaseUseCaseImpl(
        PhysicalCourseQuoteRepository quoteRepository, PaymentRepository paymentRepository,
        PhysicalCourseAvailabilityPort availabilityPort, BankTransferRateLimitPort rateLimitPort, Clock clock,
        BankAccountDetails bankAccountDetails
    ) {
        this.quoteRepository = quoteRepository;
        this.paymentRepository = paymentRepository;
        this.availabilityPort = availabilityPort;
        this.rateLimitPort = rateLimitPort;
        this.clock = clock;
        this.bankAccountDetails = bankAccountDetails;
    }

    @Override
    public PhysicalPurchaseBankTransferCheckoutResult create(CreatePhysicalPurchaseCheckoutCommand command) {
        RateLimitDecision decision = rateLimitPort.consumeBankTransferCreation(command.userId());
        if (!decision.isAllowed()) {
            throw new BankTransferRateLimitedException(decision.getRetryAfter());
        }

        String externalReference = externalReferenceFor(command.userId(), command.idempotencyKey());
        Optional<Payment> replay = paymentRepository.findByExternalReference(externalReference);
        if (replay.isPresent()) {
            // Same key, same answer, no second Payment — the budget above was already charged for
            // this request regardless (design C1's idempotency consequence).
            return toResult(replay.get());
        }

        Instant now = clock.now();
        PhysicalCourseQuote quote = quoteRepository.findById(command.quoteId())
            .filter(candidate -> candidate.getExpiresAt().isAfter(now))
            .orElseThrow(PhysicalCourseQuoteExpiredException::new);

        // D7: best-effort, non-binding — the plan is read and discarded, never followed by a hold.
        // The real capacity decision happens later, at approval, through the no-hold branch (D1).
        PhysicalCoverageAvailability.requireComplete(availabilityPort, quote, now);

        PaymentId paymentId = PaymentId.generate();
        Payment payment = paymentRepository.save(Payment.awaitingManualVerification(
            paymentId, command.userId(), quote.getAmount(), externalReference, bankAccountDetails.cbu(),
            new PaymentTarget.Physical(quote.getId().toString()), now
        ));

        return toResult(payment);
    }

    private PhysicalPurchaseBankTransferCheckoutResult toResult(Payment payment) {
        BankTransferInstructions instructions = BankTransferInstructions.of(
            bankAccountDetails, payment.getExpectedAmount(), payment.getExpectedExternalReference()
        );
        return PhysicalPurchaseBankTransferCheckoutResult.from(payment, instructions);
    }

    /**
     * Deterministic, matching design C1: the same {@code (userId, idempotencyKey)} pair always
     * yields the same reference, which is what lets {@link
     * PaymentRepository#findByExternalReference} double as the idempotency lookup with zero new
     * persistence. Prefixed {@code PHY-BT-}, never {@code PHY-} — see this class' and this
     * constant's javadoc for why the distinction is load-bearing.
     */
    private static String externalReferenceFor(UUID userId, String idempotencyKey) {
        String seed = userId + ":" + idempotencyKey;
        return EXTERNAL_REFERENCE_PREFIX + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
