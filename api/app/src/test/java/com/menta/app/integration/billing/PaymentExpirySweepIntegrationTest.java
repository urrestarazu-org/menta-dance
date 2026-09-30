package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.infrastructure.persistence.entity.PaymentProofJpaEntity;
import com.menta.billing.infrastructure.persistence.mapper.PaymentJpaMapper;
import com.menta.billing.infrastructure.persistence.mapper.SubscriptionJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PaymentProofJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionJpaRepository;
import com.menta.billing.infrastructure.scheduling.PaymentExpiryReconciler;
import com.menta.billing.infrastructure.scheduling.PaymentExpiryWorker;
import com.menta.app.integration.support.AbstractBillingMySqlIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Real {@code tick()} coverage for {@link PaymentExpiryReconciler}/{@link PaymentExpiryWorker}
 * (#31, US-BILLING-003, design C6) against a real MySQL (Testcontainers) — the success criterion
 * for the whole change (tasks.md P5). {@code billing.bank-transfer.expiry.rate-ms} is set very
 * high so the {@code @Scheduled} job never fires on its own during the test; every assertion
 * drives {@code tick()} manually. Mirrors {@code SubscriptionExpirySweepIntegrationTest}'s shape
 * exactly, including its minimal mock set.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
class PaymentExpirySweepIntegrationTest extends AbstractBillingMySqlIntegrationTest {

    private static final String HMAC_SECRET = "integration-test-payment-expiry-secret";
    private static final String MERCHANT_ACCOUNT_ID = "merchant-payment-expiry";

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("billing.webhook.mercadopago.hmac-secret", () -> HMAC_SECRET);
        registry.add("billing.webhook.reconcile-rate-ms", () -> "999999999");
        registry.add("billing.mercadopago.merchant-account-id", () -> MERCHANT_ACCOUNT_ID);
        // The scheduled tick must never fire on its own — every assertion drives tick() by hand.
        registry.add("billing.bank-transfer.expiry.rate-ms", () -> "999999999");
        // The shared integration-test profile defaults this reconciler off (see
        // application-integration-test.yml); this class is the one place that needs the real
        // bean to exist, since it wires PaymentExpiryReconciler directly.
        registry.add("billing.bank-transfer.expiry.enabled", () -> "true");
    }

    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private PaymentProofJpaRepository paymentProofRepository;
    @Autowired private SubscriptionJpaRepository subscriptionRepository;
    @Autowired private SubscriptionCourseJpaRepository subscriptionCourseRepository;
    @Autowired private PurchaseJpaRepository purchaseRepository;
    @Autowired private PaymentExpiryReconciler reconciler;
    @Autowired private ApplicationContext context;

    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private RedisTemplate<String, String> redisTemplate;
    @MockBean private LoginRateLimitPort loginRateLimitPort;

    @BeforeEach
    void stubDefensively() {
        when(tokenBlacklistPort.isBlacklisted(anyString())).thenReturn(false);
        when(tokenBlacklistPort.currentTokenVersion(anyString())).thenReturn(java.util.OptionalLong.empty());
        when(authDegradedGuard.isDegraded()).thenReturn(false);
    }

    @AfterEach
    void cleanUp() {
        paymentProofRepository.deleteAll();
        subscriptionCourseRepository.deleteAll();
        subscriptionRepository.deleteAll();
        purchaseRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    // --- fixtures -------------------------------------------------------------

    /** Same microsecond-truncation rationale {@code SubscriptionExpirySweepIntegrationTest} documents. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private PaymentId seedAwaitingManualVerificationPayment(UUID ownerId, Instant createdAt) {
        PaymentId paymentId = PaymentId.generate();
        Payment payment = Payment.awaitingManualVerification(
            paymentId, ownerId, Money.of(new BigDecimal("15000.00"), "ARS"), "SUB-" + paymentId,
            "0000003100000000000000", new PaymentTarget.Virtual("plan-1"), createdAt
        );
        paymentRepository.save(PaymentJpaMapper.toEntity(payment));
        subscriptionRepository.save(SubscriptionJpaMapper.toEntity(Subscription.pendingCheckout(
            UUID.randomUUID(), paymentId, ownerId, PlanId.generate(), "idem-" + paymentId, createdAt
        )));
        return paymentId;
    }

    /**
     * #36 P5, C7 characterization fixture: a bank-transfer-origin physical payment, same
     * real-persistence shape {@code CreateBankTransferPhysicalPurchaseUseCaseImpl} produces. No
     * {@code Purchase} row is ever seeded alongside it — D1 never reserves capacity at creation,
     * so there is nothing for expiry to release (D5 composes with D1: nothing was held).
     */
    private PaymentId seedAwaitingManualVerificationPhysicalPayment(UUID ownerId, Instant createdAt) {
        PaymentId paymentId = PaymentId.generate();
        Payment payment = Payment.awaitingManualVerification(
            paymentId, ownerId, Money.of(new BigDecimal("300.00"), "ARS"), "PHY-BT-" + paymentId,
            "0000003100000000000000", new PaymentTarget.Physical(UUID.randomUUID().toString()), createdAt
        );
        paymentRepository.save(PaymentJpaMapper.toEntity(payment));
        return paymentId;
    }

    private void seedProofFor(PaymentId paymentId) {
        paymentProofRepository.save(new PaymentProofJpaEntity(
            UUID.randomUUID(), paymentId.getValue(), "proofs/" + paymentId, "comprobante.png", "image/png", 1024L,
            now()
        ));
    }

    // --- P5 success criterion: both rows commit together -----------------------

    @Test
    void a_stale_unproven_payment_expires_and_cancels_the_subscription() {
        Instant createdAt = now().minus(73, ChronoUnit.HOURS);
        UUID owner = UUID.randomUUID();
        PaymentId paymentId = seedAwaitingManualVerificationPayment(owner, createdAt);

        reconciler.tick();

        assertThat(paymentRepository.findById(paymentId.getValue()).orElseThrow().getStatusType())
            .isEqualTo("EXPIRED");
        assertThat(subscriptionRepository.findAll().get(0).getStatus()).isEqualTo("CANCELLED");
    }

    // --- spec: "A submitted proof withholds automatic expiry" ------------------

    @Test
    void a_stale_payment_with_a_submitted_proof_is_left_untouched() {
        Instant createdAt = now().minus(73, ChronoUnit.HOURS);
        UUID owner = UUID.randomUUID();
        PaymentId paymentId = seedAwaitingManualVerificationPayment(owner, createdAt);
        seedProofFor(paymentId);

        reconciler.tick();

        assertThat(paymentRepository.findById(paymentId.getValue()).orElseThrow().getStatusType())
            .isEqualTo("AWAITING_MANUAL_VERIFICATION");
        assertThat(subscriptionRepository.findAll().get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void a_recent_unproven_payment_is_left_untouched() {
        Instant createdAt = now().minus(1, ChronoUnit.HOURS);
        UUID owner = UUID.randomUUID();
        PaymentId paymentId = seedAwaitingManualVerificationPayment(owner, createdAt);

        reconciler.tick();

        assertThat(paymentRepository.findById(paymentId.getValue()).orElseThrow().getStatusType())
            .isEqualTo("AWAITING_MANUAL_VERIFICATION");
    }

    // --- #36 P5 (C7): a physical bank-transfer payment expires the same way -----

    /**
     * C7's characterization proof: {@code findExpirableBankTransferIds} filters only on {@code
     * status_type}/{@code createdAt}/proof-absence (no {@code targetModality} predicate) and
     * {@code expireOne} never calls {@code ensure} — so a stale, unproven PHYSICAL payment expires
     * exactly like a VIRTUAL one, and leaves ZERO {@code billing_purchases} rows (D1: nothing was
     * ever reserved, so there is nothing to release).
     */
    @Test
    void a_stale_physical_bank_transfer_payment_with_no_proof_expires_and_creates_no_purchase() {
        Instant createdAt = now().minus(73, ChronoUnit.HOURS);
        UUID owner = UUID.randomUUID();
        PaymentId paymentId = seedAwaitingManualVerificationPhysicalPayment(owner, createdAt);

        reconciler.tick();

        assertThat(paymentRepository.findById(paymentId.getValue()).orElseThrow().getStatusType())
            .isEqualTo("EXPIRED");
        assertThat(purchaseRepository.findByPaymentId(paymentId.getValue())).isEmpty();
        assertThat(purchaseRepository.findAll()).isEmpty();
    }

    // --- Wiring (scan) -----------------------------------------------------------

    @Test
    void sweepBeansAreScannedOnce() {
        assertThat(context.getBeansOfType(PaymentExpiryReconciler.class)).hasSize(1);
        assertThat(context.getBeansOfType(PaymentExpiryWorker.class)).hasSize(1);
    }
}
