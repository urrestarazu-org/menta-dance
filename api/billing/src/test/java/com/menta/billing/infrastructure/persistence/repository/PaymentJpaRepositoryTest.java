package com.menta.billing.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.infrastructure.persistence.entity.PaymentJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PaymentProofJpaEntity;
import com.menta.billing.infrastructure.persistence.projection.PendingVerificationRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

/**
 * #33, US-BILLING-005, design C10 — the constructor-expression projection needs an explicit
 * {@code countQuery} (Spring Data cannot derive one from a constructor expression), so this needs
 * a real query execution, not a mock.
 */
@DataJpaTest
@ContextConfiguration(classes = PaymentJpaRepositoryTest.JpaConfiguration.class)
class PaymentJpaRepositoryTest {

    private static final Instant BASE = Instant.parse("2026-09-01T10:00:00Z");

    @Autowired private PaymentJpaRepository paymentJpaRepository;
    @Autowired private PaymentProofJpaRepository paymentProofJpaRepository;

    @Configuration
    @EntityScan(basePackageClasses = PaymentJpaEntity.class)
    @EnableJpaRepositories(basePackageClasses = PaymentJpaRepository.class)
    static class JpaConfiguration {
    }

    private PaymentJpaEntity awaitingManualVerification(UUID id, Instant createdAt) {
        return new PaymentJpaEntity(
            id, UUID.randomUUID(), null, BigDecimal.TEN, "ARS", "ext-" + id, "merchant-1", "SUBSCRIPTION",
            "plan-basic", "AWAITING_MANUAL_VERIFICATION", null, null, createdAt
        );
    }

    @Test
    void returns_oldest_first_with_hasProof_correlated_via_the_explicit_count_query() {
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        paymentJpaRepository.saveAndFlush(awaitingManualVerification(older, BASE));
        paymentJpaRepository.saveAndFlush(awaitingManualVerification(newer, BASE.plusSeconds(60)));
        paymentProofJpaRepository.saveAndFlush(new PaymentProofJpaEntity(
            UUID.randomUUID(), older, "key-1", "comprobante.png", "image/png", 100L, BASE
        ));

        Page<PendingVerificationRow> page = paymentJpaRepository.findByStatusTypeOrderByCreatedAt(
            "AWAITING_MANUAL_VERIFICATION", PageRequest.of(0, 20)
        );

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(PendingVerificationRow::id).containsExactly(older, newer);
        assertThat(page.getContent().get(0).proofCount()).isEqualTo(1L);
        assertThat(page.getContent().get(1).proofCount()).isEqualTo(0L);
    }

    @Test
    void excludes_payments_in_a_different_status() {
        paymentJpaRepository.saveAndFlush(new PaymentJpaEntity(
            UUID.randomUUID(), UUID.randomUUID(), "mp-1", BigDecimal.TEN, "ARS", "ext-completed",
            "merchant-1", "SUBSCRIPTION", "plan-basic", "COMPLETED", null, BASE, BASE
        ));

        Page<PendingVerificationRow> page = paymentJpaRepository.findByStatusTypeOrderByCreatedAt(
            "AWAITING_MANUAL_VERIFICATION", PageRequest.of(0, 20)
        );

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }
}
