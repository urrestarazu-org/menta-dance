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
import com.menta.billing.infrastructure.persistence.repository.SubscriptionCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionJpaRepository;
import com.menta.billing.infrastructure.scheduling.PaymentExpiryReconciler;
import com.menta.billing.infrastructure.scheduling.PaymentExpiryWorker;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
@Testcontainers
class PaymentExpirySweepIntegrationTest {

    private static final String HMAC_SECRET = "integration-test-payment-expiry-secret";
    private static final String MERCHANT_ACCOUNT_ID = "merchant-payment-expiry";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
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

    // --- Wiring (scan) -----------------------------------------------------------

    @Test
    void sweepBeansAreScannedOnce() {
        assertThat(context.getBeansOfType(PaymentExpiryReconciler.class)).hasSize(1);
        assertThat(context.getBeansOfType(PaymentExpiryWorker.class)).hasSize(1);
    }
}
