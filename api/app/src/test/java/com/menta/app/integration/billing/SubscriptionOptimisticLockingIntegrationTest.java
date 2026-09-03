package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.model.FulfillmentStatus;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.SubscriptionStatus;
import com.menta.billing.domain.model.SubscriptionType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real optimistic-locking coverage for {@code @Version} (US-BILLING-012, design A14). Two reads
 * of the same row followed by two writes reproduce the exact interleaving a real race would
 * cause, without needing actual threads — the same technique the design doc itself describes
 * ("load the same subscription twice, save one copy, then save the stale copy").
 *
 * <p>{@link SubscriptionRepository#save(Subscription)} is {@code Propagation.MANDATORY}, so each
 * write below runs inside its own {@code REQUIRES_NEW} transaction via {@link
 * TransactionTemplate} — exactly the shape {@code SubscriptionExpiryWorker} and the cancellation
 * use cases already use in production.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@Testcontainers
class SubscriptionOptimisticLockingIntegrationTest {

    private static final String HMAC_SECRET = "integration-test-locking-secret";
    private static final String MERCHANT_ACCOUNT_ID = "merchant-locking";

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
        // OutboxBlacklistReconciler's real @Scheduled tick (default 5s) calls
        // tokenBlacklistPort.writeHeartbeat() — a void method — on this class's @MockBean.
        // Left at its default, a background tick can fire mid-@BeforeEach while Mockito is
        // still recording when(tokenBlacklistPort.isBlacklisted(...)), corrupting Mockito's
        // stubbing state and throwing CannotStubVoidMethodWithReturnValue nondeterministically.
        registry.add("auth.outbox.reconcile-rate-ms", () -> "999999999");
        registry.add("billing.mercadopago.merchant-account-id", () -> MERCHANT_ACCOUNT_ID);
        registry.add("billing.subscription.expiry.rate-ms", () -> "999999999");
    }

    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private RedisTemplate<String, String> redisTemplate;
    @MockBean private LoginRateLimitPort loginRateLimitPort;

    private TransactionTemplate requiresNew;

    @BeforeEach
    void setUp() {
        when(tokenBlacklistPort.isBlacklisted(anyString())).thenReturn(false);
        when(tokenBlacklistPort.currentTokenVersion(anyString())).thenReturn(java.util.OptionalLong.empty());
        when(authDegradedGuard.isDegraded()).thenReturn(false);

        requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    private UUID seedActivePaidSubscription(UUID userId, UUID planId, Instant startDate, Instant endDate) {
        Subscription created = new Subscription(
            UUID.randomUUID(), PaymentId.generate(), userId, PlanId.of(planId), "idem-" + UUID.randomUUID(),
            SubscriptionStatus.ACTIVE, FulfillmentStatus.ASSIGNED, startDate, endDate, List.of("course-1"), null,
            null, startDate, null, SubscriptionType.PAID, null
        );
        return requiresNew.execute(status -> subscriptionRepository.saveNewSubscription(created).getId());
    }

    // --- Generic optimistic lock (A14) -----------------------------------------

    @Test
    void concurrent_writes_on_the_same_version_throw_optimistic_locking_exception() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Instant now = Instant.now();
        UUID subscriptionId = seedActivePaidSubscription(userId, planId, now.minusSeconds(600), now.plusSeconds(600));

        Subscription copyOne = requiresNew.execute(
            status -> subscriptionRepository.findById(subscriptionId).orElseThrow()
        );
        Subscription copyTwo = requiresNew.execute(
            status -> subscriptionRepository.findById(subscriptionId).orElseThrow()
        );

        requiresNew.execute(status -> subscriptionRepository.save(copyOne.cancel(userId, "primer motivo", now)));

        assertThatThrownBy(() -> requiresNew.execute(
            status -> subscriptionRepository.save(copyTwo.cancel(userId, "segundo motivo", now))
        )).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // --- A concurrent cancellation survives a racing sweep write (A14) ---------

    @Test
    void a_cancellation_committed_between_the_sweeps_read_and_write_leaves_the_cancellation_audit_intact() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant past = now.minusSeconds(3600);
        UUID subscriptionId = seedActivePaidSubscription(userId, planId, past.minusSeconds(600), past);

        // The sweep's read — a stale snapshot, version 0, taken before the cancellation commits.
        Subscription staleSweepSnapshot = requiresNew.execute(
            status -> subscriptionRepository.findById(subscriptionId).orElseThrow()
        );

        // A concurrent admin cancellation commits in between: reads its own fresh copy and writes it.
        Subscription cancelledByAdmin = requiresNew.execute(
            status -> subscriptionRepository.findById(subscriptionId).orElseThrow()
        );
        requiresNew.execute(
            status -> subscriptionRepository.save(cancelledByAdmin.cancel(adminId, "cambio de plan", now))
        );

        // The sweep now attempts to persist its stale expire() result — still carrying version 0.
        Subscription staleExpiryAttempt = staleSweepSnapshot.expire(now);
        assertThatThrownBy(() -> requiresNew.execute(status -> subscriptionRepository.save(staleExpiryAttempt)))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // The cancellation's audit trail was never overwritten by the losing sweep write.
        Subscription current = subscriptionRepository.findById(subscriptionId).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }
}
