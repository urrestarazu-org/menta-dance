package com.menta.billing.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.infrastructure.persistence.entity.PaymentJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PaymentProofJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PhysicalCourseQuoteJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.SubscriptionJpaEntity;
import com.menta.billing.infrastructure.persistence.projection.OwnPaymentRow;
import com.menta.billing.infrastructure.persistence.projection.PendingVerificationRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
    @Autowired private SubscriptionJpaRepository subscriptionJpaRepository;
    @Autowired private PlanJpaRepository planJpaRepository;
    @Autowired private PhysicalCourseQuoteJpaRepository quoteJpaRepository;

    @Configuration
    @EntityScan(basePackageClasses = PaymentJpaEntity.class)
    @EnableJpaRepositories(basePackageClasses = PaymentJpaRepository.class)
    static class JpaConfiguration {
    }

    private PaymentJpaEntity awaitingManualVerification(UUID id, Instant createdAt) {
        return new PaymentJpaEntity(
            id, UUID.randomUUID(), null, BigDecimal.TEN, "ARS", "ext-" + id, "merchant-1", "BANK_TRANSFER",
            "SUBSCRIPTION",
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
            "merchant-1", "MERCADO_PAGO", "SUBSCRIPTION", "plan-basic", "COMPLETED", null, BASE, BASE
        ));

        Page<PendingVerificationRow> page = paymentJpaRepository.findByStatusTypeOrderByCreatedAt(
            "AWAITING_MANUAL_VERIFICATION", PageRequest.of(0, 20)
        );

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    // --- #767: the student's own payments ------------------------------------------------------

    private UUID ownedPayment(UUID userId, String modality, String reference, String paymentMethod, Instant createdAt) {
        UUID id = UUID.randomUUID();
        paymentJpaRepository.saveAndFlush(new PaymentJpaEntity(
            id, userId, null, BigDecimal.TEN, "ARS", "ext-" + id, "merchant-1", paymentMethod, modality, reference,
            "AWAITING_PROVIDER", null, null, createdAt
        ));
        return id;
    }

    private void ownedPayment(UUID id, UUID userId, Instant createdAt) {
        paymentJpaRepository.saveAndFlush(new PaymentJpaEntity(
            id, userId, null, BigDecimal.TEN, "ARS", "ext-" + id, "merchant-1", "MERCADO_PAGO", "VIRTUAL", "plan-x",
            "AWAITING_PROVIDER", null, null, createdAt
        ));
    }

    private UUID subscriptionPayment(
        UUID userId, String planName, String paymentMethod, String providerPreferenceId, Instant createdAt
    ) {
        UUID planId = UUID.randomUUID();
        planJpaRepository.saveAndFlush(new PlanJpaEntity(
            planId, planName, "desc", BigDecimal.TEN, "ARS", 30, false, PlanStatus.ACTIVE, "terms", "policy", BASE, BASE
        ));
        UUID paymentId = ownedPayment(userId, "VIRTUAL", planId.toString(), paymentMethod, createdAt);
        UUID subscriptionId = UUID.randomUUID();
        subscriptionJpaRepository.saveAndFlush(new SubscriptionJpaEntity(
            subscriptionId, paymentId, userId, planId, "idem-" + subscriptionId, null, "PENDING", "ASSIGNED",
            null, null, providerPreferenceId, null, createdAt, null, null, null, "PAID", null, null, null, null, 0L
        ));
        return paymentId;
    }

    private UUID physicalPayment(UUID userId, String courseId, String paymentMethod, Instant createdAt) {
        String quoteId = UUID.randomUUID().toString();
        quoteJpaRepository.saveAndFlush(new PhysicalCourseQuoteJpaEntity(
            quoteId, courseId, "MONTHLY", BigDecimal.TEN, "ARS", BigDecimal.ZERO, 1, 4, null, BigDecimal.TEN, "ARS",
            "AVAILABLE", BASE, BASE.plusSeconds(900)
        ));
        return ownedPayment(userId, "PHYSICAL", quoteId, paymentMethod, createdAt);
    }

    @Test
    void own_payments_resolve_the_plan_name_and_the_quote_course_in_the_same_query() {
        UUID userId = UUID.randomUUID();
        UUID subscription = subscriptionPayment(userId, "Plan Mensual", "MERCADO_PAGO", "pref-1", BASE);
        UUID physical = physicalPayment(userId, "course-1", "BANK_TRANSFER", BASE.plusSeconds(60));

        List<OwnPaymentRow> rows = paymentJpaRepository.findOwnNewestFirst(userId, PageRequest.of(0, 20));

        assertThat(rows).extracting(OwnPaymentRow::id).containsExactly(physical, subscription);
        assertThat(rows.get(0).courseId()).isEqualTo("course-1");
        assertThat(rows.get(0).planName()).isNull();
        assertThat(rows.get(1).planName()).isEqualTo("Plan Mensual");
        assertThat(rows.get(1).courseId()).isNull();
    }

    @Test
    void own_payments_carry_the_stored_payment_method_whatever_the_reference_or_the_subscription_say() {
        UUID userId = UUID.randomUUID();
        // The subscription has a checkout preference (what the old deduction read as Mercado Pago), yet the
        // stored method wins; and a physical payment keeps its method whatever its reference looks like.
        UUID subscription = subscriptionPayment(userId, "Plan Mensual", "BANK_TRANSFER", "pref-1", BASE);
        UUID physical = physicalPayment(userId, "course-1", "MERCADO_PAGO", BASE.plusSeconds(60));

        List<OwnPaymentRow> rows = paymentJpaRepository.findOwnNewestFirst(userId, PageRequest.of(0, 20));

        assertThat(rows).extracting(OwnPaymentRow::id).containsExactly(physical, subscription);
        assertThat(rows).extracting(OwnPaymentRow::paymentMethod).containsExactly("MERCADO_PAGO", "BANK_TRANSFER");
    }

    @Test
    void own_payments_exclude_other_users_and_honour_the_limit() {
        UUID userId = UUID.randomUUID();
        ownedPayment(UUID.randomUUID(), "VIRTUAL", "plan-x", "MERCADO_PAGO", BASE.plusSeconds(600));
        UUID oldest = ownedPayment(userId, "VIRTUAL", "plan-x", "MERCADO_PAGO", BASE);
        UUID middle = ownedPayment(userId, "VIRTUAL", "plan-x", "MERCADO_PAGO", BASE.plusSeconds(60));
        UUID newest = ownedPayment(userId, "VIRTUAL", "plan-x", "MERCADO_PAGO", BASE.plusSeconds(120));

        assertThat(paymentJpaRepository.findOwnNewestFirst(userId, PageRequest.of(0, 20)))
            .extracting(OwnPaymentRow::id).containsExactly(newest, middle, oldest);
        assertThat(paymentJpaRepository.findOwnNewestFirst(userId, PageRequest.of(0, 2)))
            .extracting(OwnPaymentRow::id).containsExactly(newest, middle);
    }

    @Test
    void own_payments_created_at_the_same_instant_are_ordered_by_id_descending() {
        UUID userId = UUID.randomUUID();
        UUID lower = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higher = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ownedPayment(lower, userId, BASE);
        ownedPayment(higher, userId, BASE);

        assertThat(paymentJpaRepository.findOwnNewestFirst(userId, PageRequest.of(0, 20)))
            .extracting(OwnPaymentRow::id).containsExactly(higher, lower);
    }
}
