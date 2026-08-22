package com.menta.billing.infrastructure.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentStatus;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.infrastructure.persistence.entity.PaymentJpaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentJpaMapperTest {

    private static final Instant NOW = Instant.parse("2026-08-18T12:00:00Z");
    private static final Money AMOUNT = Money.of(BigDecimal.TEN, "ARS");

    private static final UUID USER_ID = UUID.randomUUID();

    private static Payment payment(PaymentTarget target, PaymentStatus status) {
        return new Payment(
            PaymentId.generate(), USER_ID, "mp-1", AMOUNT, "ext-1", "merchant-1", target, status, NOW
        );
    }

    @Test
    void round_trips_a_physical_pending_payment() {
        Payment original = payment(new PaymentTarget.Physical("session-1"), new PaymentStatus.AwaitingProvider());

        PaymentJpaEntity entity = PaymentJpaMapper.toEntity(original);
        Payment restored = PaymentJpaMapper.toDomain(entity);

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getUserId()).isEqualTo(USER_ID);
        assertThat(restored.getTarget()).isEqualTo(original.getTarget());
        assertThat(restored.getStatus()).isEqualTo(original.getStatus());
    }

    @Test
    void round_trips_a_virtual_reconciliation_required_payment() {
        Payment original =
            payment(new PaymentTarget.Virtual("plan-1"), new PaymentStatus.ReconciliationRequired("mismatch"));

        Payment restored = PaymentJpaMapper.toDomain(PaymentJpaMapper.toEntity(original));

        assertThat(restored.getTarget()).isEqualTo(original.getTarget());
        assertThat(restored.getStatus()).isEqualTo(original.getStatus());
    }

    @Test
    void round_trips_every_terminal_status() {
        for (PaymentStatus status : new PaymentStatus[] {
            new PaymentStatus.Completed(NOW), new PaymentStatus.Rejected(NOW),
            new PaymentStatus.Cancelled(NOW), new PaymentStatus.Expired(NOW),
            new PaymentStatus.AwaitingManualVerification()
        }) {
            Payment original = payment(new PaymentTarget.Physical("session-1"), status);

            Payment restored = PaymentJpaMapper.toDomain(PaymentJpaMapper.toEntity(original));

            assertThat(restored.getStatus()).isEqualTo(status);
        }
    }

    /** US-BILLING-010: a checkout payment has no provider id, and the column must carry that absence. */
    @Test
    void round_trips_an_unbound_checkout_payment() {
        Payment original = Payment.awaitingProvider(
            PaymentId.generate(), USER_ID, AMOUNT, "SUB-ext", "merchant-1",
            new PaymentTarget.Virtual("plan-1"), NOW
        );

        PaymentJpaEntity entity = PaymentJpaMapper.toEntity(original);
        Payment restored = PaymentJpaMapper.toDomain(entity);

        assertThat(entity.getProviderPaymentId()).isNull();
        assertThat(restored.getProviderPaymentId()).isEmpty();
        assertThat(restored.isBound()).isFalse();
        assertThat(restored.getExpectedExternalReference()).isEqualTo("SUB-ext");
    }
}
