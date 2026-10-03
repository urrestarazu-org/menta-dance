package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.BankAccountDetails;
import com.menta.billing.application.dto.CreatePhysicalPurchaseCheckoutCommand;
import com.menta.billing.application.dto.PaymentPreferenceResult;
import com.menta.billing.application.dto.PhysicalPurchaseBankTransferCheckoutResult;
import com.menta.billing.application.dto.RateLimitDecision;
import com.menta.billing.application.dto.ScheduledSessionSnapshot;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PhysicalCapacityHoldPort;
import com.menta.billing.application.port.out.PhysicalCourseAvailabilityPort;
import com.menta.billing.application.port.out.PhysicalCourseQuoteRepository;
import com.menta.billing.domain.exception.BankTransferRateLimitedException;
import com.menta.billing.domain.exception.PhysicalCapacityUnavailableException;
import com.menta.billing.domain.exception.PhysicalCourseQuoteExpiredException;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.PhysicalCoursePricing;
import com.menta.billing.domain.model.PhysicalCourseQuote;
import com.menta.billing.domain.model.QuoteAvailability;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class CreateBankTransferPhysicalPurchaseUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String COURSE_ID = "course-1";
    private static final BankAccountDetails ACCOUNT =
        new BankAccountDetails("0000003100000000000000", "menta.dance", "Menta Dance SRL", "30-00000000-0");

    private PhysicalCourseQuoteRepository quoteRepository;
    private PaymentRepository paymentRepository;
    private PhysicalCourseAvailabilityPort availabilityPort;
    private BankTransferRateLimitPort rateLimitPort;
    private Clock clock;
    private CreateBankTransferPhysicalPurchaseUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        quoteRepository = mock(PhysicalCourseQuoteRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        availabilityPort = mock(PhysicalCourseAvailabilityPort.class);
        rateLimitPort = mock(BankTransferRateLimitPort.class);
        clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        when(rateLimitPort.consumeBankTransferCreation(any())).thenReturn(RateLimitDecision.allowed());
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByExternalReference(any())).thenReturn(Optional.empty());
        useCase = new CreateBankTransferPhysicalPurchaseUseCaseImpl(
            quoteRepository, paymentRepository, availabilityPort, rateLimitPort, clock, ACCOUNT
        );
    }

    private static PhysicalCoursePricing pricing() {
        return PhysicalCoursePricing.createFirstVersion(
            COURSE_ID, Money.of(new BigDecimal("10000.00"), "ARS"), new BigDecimal("20.00"), NOW
        );
    }

    private static PhysicalCourseQuote monthlyQuote(int scheduledSessionCount, Instant createdAt) {
        return PhysicalCourseQuote.monthly(
            COURSE_ID, pricing(), scheduledSessionCount, QuoteAvailability.AVAILABLE, createdAt
        );
    }

    private static CreatePhysicalPurchaseCheckoutCommand command(String quoteId, String idempotencyKey) {
        return new CreatePhysicalPurchaseCheckoutCommand(USER_ID, quoteId, PaymentMethod.BANK_TRANSFER, idempotencyKey);
    }

    // #208-style helper, same shape as CreatePhysicalPurchaseCheckoutUseCaseImplTest's own.
    private static List<ScheduledSessionSnapshot> sessionsFrom(Instant start, int count, int availableSpots) {
        return IntStream.range(0, count)
            .mapToObj(i -> new ScheduledSessionSnapshot(
                UUID.randomUUID().toString(), start.plus(Duration.ofDays(i)), availableSpots
            ))
            .toList();
    }

    // --- Scenario 1: happy path, no hold, no provider call (D1) ---

    /**
     * Design D1: the constructor accepts no {@code PhysicalCapacityHoldPort} and no {@code
     * PaymentPreferencePort} at all (design C2) — the structural absence of those collaborators is
     * itself the proof that creation never holds capacity or calls a provider, stronger than a
     * per-test {@code verifyNoInteractions} could be on a mock this class cannot even reach.
     */
    @Test
    void a_valid_non_expired_non_full_quote_creates_exactly_one_payment_awaiting_manual_verification() {
        PhysicalCourseQuote quote = monthlyQuote(4, NOW.minusSeconds(60));
        when(quoteRepository.findById(quote.getId().toString())).thenReturn(Optional.of(quote));
        when(availabilityPort.findScheduledSessions(any(), any(), any())).thenReturn(sessionsFrom(NOW, 4, 5));

        PhysicalPurchaseBankTransferCheckoutResult result = useCase.create(command(quote.getId().toString(), "idem-1"));

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertThat(payment.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(payment.getValue().getExpectedAmount()).isEqualTo(quote.getAmount());
        assertThat(payment.getValue().getExpectedMerchantAccountId()).isEqualTo(ACCOUNT.cbu());
        assertThat(payment.getValue().getStatus()).isEqualTo(new PaymentStatus.AwaitingManualVerification());
        assertThat(payment.getValue().getTarget()).isEqualTo(new PaymentTarget.Physical(quote.getId().toString()));

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.quoteId()).isEqualTo(quote.getId().toString());
        assertThat(result.paymentId()).isEqualTo(payment.getValue().getId().toString());
        assertThat(result.bankTransferInstructions()).isEqualTo(
            com.menta.billing.application.dto.BankTransferInstructions.of(ACCOUNT, quote.getAmount(), result.externalReference())
        );
    }

    // --- Scenario 3: exhausted daily budget (429) ---

    /** Design C2/C7: the budget is consumed before any read or write, matching the subscription rail. */
    @Test
    void a_rate_limited_user_is_rejected_with_zero_other_port_interactions() {
        when(rateLimitPort.consumeBankTransferCreation(USER_ID))
            .thenReturn(RateLimitDecision.limited(Duration.ofHours(2)));

        assertThatThrownBy(() -> useCase.create(command(UUID.randomUUID().toString(), "idem-1")))
            .isInstanceOf(BankTransferRateLimitedException.class)
            .satisfies(thrown -> assertThat(((BankTransferRateLimitedException) thrown).getRetryAfter())
                .isEqualTo(Duration.ofHours(2)));

        verify(paymentRepository, never()).findByExternalReference(any());
        verify(paymentRepository, never()).save(any());
        verify(quoteRepository, never()).findById(any());
        verify(availabilityPort, never()).findScheduledSessions(any(), any(), any());
    }

    // --- Scenario 2: expired or unknown quote (410) ---

    @Test
    void an_expired_or_unknown_quote_is_rejected_with_zero_writes() {
        when(quoteRepository.findById("does-not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.create(command("does-not-exist", "idem-1")))
            .isInstanceOf(PhysicalCourseQuoteExpiredException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void an_actually_expired_quote_is_also_rejected_as_410() {
        PhysicalCourseQuote quote = monthlyQuote(4, NOW.minus(Duration.ofHours(2)));
        when(quoteRepository.findById(quote.getId().toString())).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> useCase.create(command(quote.getId().toString(), "idem-1")))
            .isInstanceOf(PhysicalCourseQuoteExpiredException.class);

        verify(paymentRepository, never()).save(any());
        verify(availabilityPort, never()).findScheduledSessions(any(), any(), any());
    }

    // --- Scenario 4: visibly-full quote (409), best-effort, non-binding (D7) ---

    @Test
    void a_visibly_full_quote_is_rejected_with_zero_writes_and_no_hold_attempted() {
        PhysicalCourseQuote quote = monthlyQuote(4, NOW.minusSeconds(60));
        when(quoteRepository.findById(quote.getId().toString())).thenReturn(Optional.of(quote));
        when(availabilityPort.findScheduledSessions(any(), any(), any())).thenReturn(sessionsFrom(NOW, 4, 0));

        assertThatThrownBy(() -> useCase.create(command(quote.getId().toString(), "idem-1")))
            .isInstanceOf(PhysicalCapacityUnavailableException.class)
            .satisfies(thrown -> assertThat(((PhysicalCapacityUnavailableException) thrown).getErrorCode())
                .isEqualTo("CAPACITY_UNAVAILABLE"));

        verify(paymentRepository, never()).save(any());
    }

    // --- Scenario 5: idempotent replay ---

    @Test
    void a_replayed_request_returns_the_same_payment_without_a_second_write() {
        PhysicalCourseQuote quote = monthlyQuote(4, NOW.minusSeconds(60));
        when(quoteRepository.findById(quote.getId().toString())).thenReturn(Optional.of(quote));
        when(availabilityPort.findScheduledSessions(any(), any(), any())).thenReturn(sessionsFrom(NOW, 4, 5));
        CreatePhysicalPurchaseCheckoutCommand command = command(quote.getId().toString(), "idem-1");

        PhysicalPurchaseBankTransferCheckoutResult first = useCase.create(command);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        when(paymentRepository.findByExternalReference(saved.getValue().getExpectedExternalReference()))
            .thenReturn(Optional.of(saved.getValue()));

        PhysicalPurchaseBankTransferCheckoutResult second = useCase.create(command);

        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        assertThat(second.externalReference()).isEqualTo(first.externalReference());
        verify(paymentRepository, times(1)).save(any());
        // Design C1: a replay still costs one unit — each request, replay or not, is charged.
        verify(rateLimitPort, times(2)).consumeBankTransferCreation(USER_ID);
    }

    // --- Reference determinism ---

    @Test
    void the_same_user_and_idempotency_key_always_yield_the_same_reference() {
        PhysicalCourseQuote quote = monthlyQuote(4, NOW.minusSeconds(60));
        when(quoteRepository.findById(quote.getId().toString())).thenReturn(Optional.of(quote));
        when(availabilityPort.findScheduledSessions(any(), any(), any())).thenReturn(sessionsFrom(NOW, 4, 5));
        CreatePhysicalPurchaseCheckoutCommand command = command(quote.getId().toString(), "idem-1");

        PhysicalPurchaseBankTransferCheckoutResult first = useCase.create(command);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        when(paymentRepository.findByExternalReference(saved.getValue().getExpectedExternalReference()))
            .thenReturn(Optional.of(saved.getValue()));

        PhysicalPurchaseBankTransferCheckoutResult second = useCase.create(command);

        assertThat(second.externalReference()).isEqualTo(first.externalReference());
        assertThat(first.externalReference()).startsWith("PHY-BT-");
    }

    // --- Reference collision-avoidance (design C1) ---

    /**
     * Design C1: both physical arms must never derive the same reference from the same {@code
     * (userId, idempotencyKey)} pair. Computes the Mercado Pago reference through {@link
     * CreatePhysicalPurchaseCheckoutUseCaseImpl}'s own production code (not a re-implemented
     * formula) so this proves the actual runtime behavior, not just the two string literals.
     */
    @Test
    void the_bank_transfer_reference_is_provably_different_from_the_mercado_pago_reference() {
        PhysicalCourseQuote quote = monthlyQuote(4, NOW.minusSeconds(60));
        when(quoteRepository.findById(quote.getId().toString())).thenReturn(Optional.of(quote));
        when(availabilityPort.findScheduledSessions(any(), any(), any())).thenReturn(sessionsFrom(NOW, 4, 5));

        useCase.create(new CreatePhysicalPurchaseCheckoutCommand(
            USER_ID, quote.getId().toString(), PaymentMethod.BANK_TRANSFER, "idem-collision"
        ));
        ArgumentCaptor<String> bankTransferReference = ArgumentCaptor.forClass(String.class);
        verify(paymentRepository).findByExternalReference(bankTransferReference.capture());

        PaymentPreferencePort preferencePort = mock(PaymentPreferencePort.class);
        when(preferencePort.createPreference(any()))
            .thenReturn(new PaymentPreferenceResult("pref-1", "https://mp.example/checkout/pref-1"));
        PhysicalCapacityHoldPort holdPort = mock(PhysicalCapacityHoldPort.class);
        when(holdPort.hold(any(), any())).thenReturn(List.of());
        PaymentRepository mercadoPagoPaymentRepository = mock(PaymentRepository.class);
        when(mercadoPagoPaymentRepository.findByExternalReference(any())).thenReturn(Optional.empty());
        when(mercadoPagoPaymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CreatePhysicalPurchaseCheckoutUseCaseImpl mercadoPagoUseCase = new CreatePhysicalPurchaseCheckoutUseCaseImpl(
            quoteRepository, mercadoPagoPaymentRepository, availabilityPort, holdPort, preferencePort, clock,
            "merchant-1", Duration.ofMinutes(30)
        );

        mercadoPagoUseCase.create(new CreatePhysicalPurchaseCheckoutCommand(
            USER_ID, quote.getId().toString(), PaymentMethod.MERCADO_PAGO, "idem-collision"
        ));
        ArgumentCaptor<String> mercadoPagoReference = ArgumentCaptor.forClass(String.class);
        verify(mercadoPagoPaymentRepository).findByExternalReference(mercadoPagoReference.capture());

        assertThat(bankTransferReference.getValue()).isNotEqualTo(mercadoPagoReference.getValue());
        assertThat(bankTransferReference.getValue()).startsWith("PHY-BT-");
        assertThat(mercadoPagoReference.getValue()).startsWith("PHY-").doesNotStartWith("PHY-BT-");
    }

    // --- Collaborator order (design C2) ---

    @Test
    void collaborators_are_consulted_in_budget_replay_quote_availability_save_order() {
        PhysicalCourseQuote quote = monthlyQuote(4, NOW.minusSeconds(60));
        when(quoteRepository.findById(quote.getId().toString())).thenReturn(Optional.of(quote));
        when(availabilityPort.findScheduledSessions(any(), any(), any())).thenReturn(sessionsFrom(NOW, 4, 5));

        useCase.create(command(quote.getId().toString(), "idem-1"));

        InOrder order = inOrder(rateLimitPort, paymentRepository, quoteRepository, availabilityPort);
        order.verify(rateLimitPort).consumeBankTransferCreation(USER_ID);
        order.verify(paymentRepository).findByExternalReference(any());
        order.verify(quoteRepository).findById(any());
        order.verify(availabilityPort).findScheduledSessions(any(), any(), any());
        order.verify(paymentRepository).save(any());
    }
}
