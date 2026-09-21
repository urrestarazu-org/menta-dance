package com.menta.billing.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.application.usecase.PaymentFulfillmentService;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unit coverage for {@link PaymentExpiryWorker} (#31, US-BILLING-003, design C6) plus the
 * Incident Guardrail #2 reflection checks this session's session-log mandates: a real transition
 * triggers exactly one {@code save} + {@code release}, a no-op transition triggers neither, and
 * the worker/reconciler split carries the exact transaction-propagation shape that already once
 * caused a CGLIB {@code AopConfigException} when violated.
 */
class PaymentExpiryWorkerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final PaymentId PAYMENT_ID = PaymentId.generate();
    private static final Money AMOUNT = Money.of(BigDecimal.TEN, "ARS");

    private static Payment paymentWith(PaymentStatus status, Instant createdAt) {
        return new Payment(
            PAYMENT_ID, USER_ID, null, AMOUNT, "ext-1", "merchant-1",
            new PaymentTarget.Virtual("plan-1"), status, createdAt
        );
    }

    @Test
    void expires_and_releases_a_real_transition() {
        PaymentRepository repository = mock(PaymentRepository.class);
        PaymentFulfillmentService fulfillmentService = mock(PaymentFulfillmentService.class);
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-09-21T00:00:00Z");
        when(clock.now()).thenReturn(now);
        when(repository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.AwaitingManualVerification(), now.minusSeconds(3600))));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        new PaymentExpiryWorker(repository, fulfillmentService, clock).expireOne(PAYMENT_ID.getValue());

        verify(repository, times(1)).save(any(Payment.class));
        verify(fulfillmentService, times(1)).release(any(Payment.class));
    }

    @Test
    void skips_save_and_release_on_a_noop() {
        PaymentRepository repository = mock(PaymentRepository.class);
        PaymentFulfillmentService fulfillmentService = mock(PaymentFulfillmentService.class);
        Clock clock = mock(Clock.class);
        Instant now = Instant.parse("2026-09-21T00:00:00Z");
        when(clock.now()).thenReturn(now);
        // Already resolved — a stale sweep tick or an admin decision that raced ahead of it.
        when(repository.findById(PAYMENT_ID))
            .thenReturn(Optional.of(paymentWith(new PaymentStatus.Completed(now), now.minusSeconds(3600))));

        new PaymentExpiryWorker(repository, fulfillmentService, clock).expireOne(PAYMENT_ID.getValue());

        verify(repository, never()).save(any(Payment.class));
        verify(fulfillmentService, never()).release(any(Payment.class));
    }

    @Test
    void does_nothing_when_the_row_disappeared_before_this_transaction() {
        PaymentRepository repository = mock(PaymentRepository.class);
        PaymentFulfillmentService fulfillmentService = mock(PaymentFulfillmentService.class);
        Clock clock = mock(Clock.class);
        UUID paymentId = UUID.randomUUID();
        when(repository.findById(any())).thenReturn(Optional.empty());

        new PaymentExpiryWorker(repository, fulfillmentService, clock).expireOne(paymentId);

        verify(repository, never()).save(any(Payment.class));
        verify(fulfillmentService, never()).release(any(Payment.class));
    }

    @Nested
    @DisplayName("Incident Guardrail #2: worker/reconciler transaction-propagation split")
    class TransactionBoundaryShape {

        @Test
        void expireOne_is_annotated_REQUIRES_NEW() throws NoSuchMethodException {
            Method expireOne = PaymentExpiryWorker.class.getMethod("expireOne", UUID.class);
            Transactional annotation = findTransactional(expireOne);

            assertThat(annotation).isNotNull();
            assertThat(annotation.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        }

        @Test
        void tick_carries_no_Transactional_annotation() throws NoSuchMethodException {
            Method tick = PaymentExpiryReconciler.class.getMethod("tick");

            assertThat(findTransactional(tick)).isNull();
            assertThat(PaymentExpiryReconciler.class.getAnnotation(Transactional.class)).isNull();
        }

        /** The reconciler must delegate to a genuinely separate bean, not call itself. */
        @Test
        void reconciler_delegates_to_a_distinct_worker_bean() {
            Constructor<?>[] constructors = PaymentExpiryReconciler.class.getConstructors();
            assertThat(constructors).hasSize(1);
            assertThat(constructors[0].getParameterTypes()).contains(PaymentExpiryWorker.class);
            assertThat(PaymentExpiryWorker.class).isNotEqualTo(PaymentExpiryReconciler.class);
        }

        private Transactional findTransactional(Method method) {
            for (Annotation annotation : method.getAnnotations()) {
                if (annotation instanceof Transactional transactional) {
                    return transactional;
                }
            }
            return null;
        }
    }
}
