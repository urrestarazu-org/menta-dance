package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.CreatePhysicalPurchaseCheckoutCommand;
import com.menta.billing.application.dto.PaymentPreferenceRequest;
import com.menta.billing.application.dto.PaymentPreferenceResult;
import com.menta.billing.application.dto.PhysicalPurchaseCheckoutResult;
import com.menta.billing.application.dto.ScheduledSessionSnapshot;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.port.out.PhysicalCourseAvailabilityPort;
import com.menta.billing.application.port.out.PhysicalCourseQuoteRepository;
import com.menta.billing.domain.exception.PaymentPreferenceUnavailableException;
import com.menta.billing.domain.exception.PhysicalCapacityUnavailableException;
import com.menta.billing.domain.exception.PhysicalCourseQuoteExpiredException;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.PhysicalCourseQuote;
import com.menta.billing.domain.model.PhysicalCoursePricing;
import com.menta.billing.domain.model.QuoteAvailability;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreatePhysicalPurchaseCheckoutUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String COURSE_ID = "course-1";
    private static final String MERCHANT_ACCOUNT_ID = "merchant-1";

    private PhysicalCourseQuoteRepository quoteRepository;
    private PaymentRepository paymentRepository;
    private PhysicalCourseAvailabilityPort availabilityPort;
    private PaymentPreferencePort paymentPreferencePort;
    private Clock clock;
    private CreatePhysicalPurchaseCheckoutUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        quoteRepository = mock(PhysicalCourseQuoteRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        availabilityPort = mock(PhysicalCourseAvailabilityPort.class);
        paymentPreferencePort = mock(PaymentPreferencePort.class);
        clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByExternalReference(any())).thenReturn(Optional.empty());
        when(paymentPreferencePort.createPreference(any()))
            .thenReturn(new PaymentPreferenceResult("pref-1", "https://mp.example/checkout/pref-1"));
        useCase = new CreatePhysicalPurchaseCheckoutUseCaseImpl(
            quoteRepository, paymentRepository, availabilityPort, paymentPreferencePort, clock, MERCHANT_ACCOUNT_ID
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
        return new CreatePhysicalPurchaseCheckoutCommand(USER_ID, quoteId, PaymentMethod.MERCADO_PAGO, idempotencyKey);
    }

    private static List<ScheduledSessionSnapshot> sessionsFrom(Instant start, int count, int availableSpots) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> new ScheduledSessionSnapshot(
                "session-" + i, start.plus(java.time.Duration.ofDays(i)), availableSpots
            ))
            .toList();
    }

    // --- Happy path ---

    @Test
    void writes_the_payment_before_calling_the_provider_and_binds_the_user_from_the_command() {
        PhysicalCourseQuote quote = monthlyQuote(4, NOW.minusSeconds(60));
        when(quoteRepository.findById(quote.getId().toString())).thenReturn(Optional.of(quote));
        when(availabilityPort.findScheduledSessions(any(), any(), any()))
            .thenReturn(sessionsFrom(NOW, 4, 5));

        PhysicalPurchaseCheckoutResult result = useCase.create(command(quote.getId().toString(), "idem-1"));

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertThat(payment.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(payment.getValue().getExpectedAmount()).isEqualTo(quote.getAmount());
        assertThat(payment.getValue().getExpectedMerchantAccountId()).isEqualTo(MERCHANT_ACCOUNT_ID);
        assertThat(payment.getValue().getStatus()).isEqualTo(new PaymentStatus.AwaitingProvider());
        assertThat(payment.getValue().getTarget()).isEqualTo(new PaymentTarget.Physical(quote.getId().toString()));

        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.quoteId()).isEqualTo(quote.getId().toString());
        assertThat(result.checkoutUrl()).isEqualTo("https://mp.example/checkout/pref-1");
        assertThat(result.providerPreferenceId()).isEqualTo("pref-1");
        assertThat(result.paymentId()).isEqualTo(payment.getValue().getId().toString());
    }

    @Test
    void bank_transfer_is_rejected_before_it_can_be_sent_to_checkout_pro() {
        CreatePhysicalPurchaseCheckoutCommand transferCommand = new CreatePhysicalPurchaseCheckoutCommand(
            USER_ID, UUID.randomUUID().toString(), PaymentMethod.BANK_TRANSFER, "idem-1"
        );

        assertThatThrownBy(() -> useCase.create(transferCommand))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Checkout Pro requires MERCADO_PAGO");

        verify(quoteRepository, never()).findById(any());
        verify(paymentRepository, never()).save(any());
        verify(paymentPreferencePort, never()).createPreference(any());
    }

    // --- A7: expired quote is 410, checked before availability ---

    @Test
    void an_expired_quote_is_rejected_without_any_write_or_provider_call() {
        PhysicalCourseQuote quote = monthlyQuote(4, NOW.minus(java.time.Duration.ofHours(2)));
        when(quoteRepository.findById(quote.getId().toString())).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> useCase.create(command(quote.getId().toString(), "idem-1")))
            .isInstanceOf(PhysicalCourseQuoteExpiredException.class)
            .satisfies(thrown -> assertThat(((PhysicalCourseQuoteExpiredException) thrown).getErrorCode())
                .isEqualTo("PHYSICAL_COURSE_QUOTE_EXPIRED"));

        verify(paymentRepository, never()).save(any());
        verify(availabilityPort, never()).findScheduledSessions(any(), any(), any());
        verify(paymentPreferencePort, never()).createPreference(any());
    }

    @Test
    void an_unknown_quote_id_is_also_rejected_as_expired_without_leaking_existence() {
        when(quoteRepository.findById("does-not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.create(command("does-not-exist", "idem-1")))
            .isInstanceOf(PhysicalCourseQuoteExpiredException.class);

        verify(paymentRepository, never()).save(any());
    }

    /** A7: expired AND full — validity wins, 410 not 409. */
    @Test
    void an_expired_and_full_quote_is_rejected_as_expired_not_as_unavailable() {
        PhysicalCourseQuote quote = monthlyQuote(4, NOW.minus(java.time.Duration.ofHours(2)));
        when(quoteRepository.findById(quote.getId().toString())).thenReturn(Optional.of(quote));
        when(availabilityPort.findScheduledSessions(any(), any(), any()))
            .thenReturn(sessionsFrom(NOW, 4, 0));

        assertThatThrownBy(() -> useCase.create(command(quote.getId().toString(), "idem-1")))
            .isInstanceOf(PhysicalCourseQuoteExpiredException.class);

        verify(availabilityPort, never()).findScheduledSessions(any(), any(), any());
    }

    // --- A6/D5: visibly-full quote is 409, best effort ---

    @Test
    void a_visibly_full_quote_is_rejected_without_any_write_or_provider_call() {
        PhysicalCourseQuote quote = monthlyQuote(4, NOW.minusSeconds(60));
        when(quoteRepository.findById(quote.getId().toString())).thenReturn(Optional.of(quote));
        when(availabilityPort.findScheduledSessions(any(), any(), any()))
            .thenReturn(sessionsFrom(NOW, 4, 0));

        assertThatThrownBy(() -> useCase.create(command(quote.getId().toString(), "idem-1")))
            .isInstanceOf(PhysicalCapacityUnavailableException.class)
            .satisfies(thrown -> assertThat(((PhysicalCapacityUnavailableException) thrown).getErrorCode())
                .isEqualTo("CAPACITY_UNAVAILABLE"));

        verify(paymentRepository, never()).save(any());
        verify(paymentPreferencePort, never()).createPreference(any());
    }

    @Test
    void the_planner_runs_with_requireAvailable_true_and_the_checkout_instant() {
        PhysicalCourseQuote quote = monthlyQuote(2, NOW.minusSeconds(60));
        when(quoteRepository.findById(quote.getId().toString())).thenReturn(Optional.of(quote));
        when(availabilityPort.findScheduledSessions(any(), any(), any()))
            .thenReturn(sessionsFrom(NOW, 2, 5));

        useCase.create(command(quote.getId().toString(), "idem-1"));

        ArgumentCaptor<Instant> periodStart = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> periodEnd = ArgumentCaptor.forClass(Instant.class);
        verify(availabilityPort).findScheduledSessions(org.mockito.ArgumentMatchers.eq(COURSE_ID),
            periodStart.capture(), periodEnd.capture());
        assertThat(periodStart.getValue()).isEqualTo(NOW);
        assertThat(periodEnd.getValue()).isEqualTo(NOW.plus(CoveragePlanner.COVERAGE_LOOKAHEAD));
    }

    // --- Idempotency ---

    @Test
    void a_replayed_idempotency_key_returns_the_same_checkout_data_without_a_second_payment() {
        PhysicalCourseQuote quote = monthlyQuote(4, NOW.minusSeconds(60));
        PaymentId existingPaymentId = PaymentId.generate();
        Payment existing = Payment.awaitingProvider(
            existingPaymentId, USER_ID, quote.getAmount(), "does-not-matter-before-lookup", MERCHANT_ACCOUNT_ID,
            new PaymentTarget.Physical(quote.getId().toString()), NOW.minusSeconds(120)
        );
        when(paymentRepository.findByExternalReference(any())).thenReturn(Optional.of(existing));

        PhysicalPurchaseCheckoutResult result = useCase.create(command(quote.getId().toString(), "idem-1"));

        assertThat(result.paymentId()).isEqualTo(existingPaymentId.toString());
        assertThat(result.quoteId()).isEqualTo(quote.getId().toString());
        verify(paymentRepository, never()).save(any());
        verify(quoteRepository, never()).findById(any());
        verify(availabilityPort, never()).findScheduledSessions(any(), any(), any());
    }

    @Test
    void two_requests_with_the_same_user_and_idempotency_key_resolve_to_the_same_external_reference() {
        PhysicalCourseQuote quote = monthlyQuote(4, NOW.minusSeconds(60));
        when(quoteRepository.findById(quote.getId().toString())).thenReturn(Optional.of(quote));
        when(availabilityPort.findScheduledSessions(any(), any(), any()))
            .thenReturn(sessionsFrom(NOW, 4, 5));

        useCase.create(command(quote.getId().toString(), "idem-1"));

        ArgumentCaptor<String> externalReference = ArgumentCaptor.forClass(String.class);
        verify(paymentRepository).findByExternalReference(externalReference.capture());

        // Recomputing with the same (userId, idempotencyKey) must produce the identical reference,
        // which is the whole idempotency mechanism (no persisted idempotency key column).
        useCase.create(command(quote.getId().toString(), "idem-1"));
        ArgumentCaptor<String> secondLookup = ArgumentCaptor.forClass(String.class);
        verify(paymentRepository, org.mockito.Mockito.times(2))
            .findByExternalReference(secondLookup.capture());
        assertThat(secondLookup.getAllValues().get(0)).isEqualTo(secondLookup.getAllValues().get(1));
    }

    // --- Provider failure ---

    @Test
    void a_provider_failure_aborts_the_checkout_after_the_payment_was_already_written() {
        PhysicalCourseQuote quote = monthlyQuote(4, NOW.minusSeconds(60));
        when(quoteRepository.findById(quote.getId().toString())).thenReturn(Optional.of(quote));
        when(availabilityPort.findScheduledSessions(any(), any(), any()))
            .thenReturn(sessionsFrom(NOW, 4, 5));
        when(paymentPreferencePort.createPreference(any())).thenThrow(new IllegalStateException("provider down"));

        assertThatThrownBy(() -> useCase.create(command(quote.getId().toString(), "idem-1")))
            .isInstanceOf(PaymentPreferenceUnavailableException.class)
            .satisfies(thrown -> assertThat(((PaymentPreferenceUnavailableException) thrown).getErrorCode())
                .isEqualTo("PAYMENT_PREFERENCE_UNAVAILABLE"));

        verify(paymentRepository).save(any());
    }

    @Test
    void sends_the_quotes_amount_and_our_external_reference_to_the_provider() {
        PhysicalCourseQuote quote = monthlyQuote(4, NOW.minusSeconds(60));
        when(quoteRepository.findById(quote.getId().toString())).thenReturn(Optional.of(quote));
        when(availabilityPort.findScheduledSessions(any(), any(), any()))
            .thenReturn(sessionsFrom(NOW, 4, 5));

        PhysicalPurchaseCheckoutResult result = useCase.create(command(quote.getId().toString(), "idem-1"));

        ArgumentCaptor<PaymentPreferenceRequest> request = ArgumentCaptor.forClass(PaymentPreferenceRequest.class);
        verify(paymentPreferencePort).createPreference(request.capture());
        assertThat(request.getValue().amount()).isEqualTo(quote.getAmount());
        assertThat(request.getValue().externalReference()).isEqualTo(result.externalReference());
    }

    @Test
    void the_command_refuses_a_request_that_does_not_identify_a_user_quote_or_key() {
        assertThatThrownBy(() -> new CreatePhysicalPurchaseCheckoutCommand(
            null, UUID.randomUUID().toString(), PaymentMethod.MERCADO_PAGO, "idem-1"
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CreatePhysicalPurchaseCheckoutCommand(
            USER_ID, " ", PaymentMethod.MERCADO_PAGO, "idem-1"
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreatePhysicalPurchaseCheckoutCommand(
            USER_ID, UUID.randomUUID().toString(), null, "idem-1"
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CreatePhysicalPurchaseCheckoutCommand(
            USER_ID, UUID.randomUUID().toString(), PaymentMethod.MERCADO_PAGO, " "
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
