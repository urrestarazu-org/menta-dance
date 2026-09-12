package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
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
 * The {@code auth.outbox.reconcile.enabled} off switch keeps {@link OutboxBlacklistReconcilerTrigger}
 * out of the context while leaving {@link OutboxBlacklistReconciler} itself in place (#172), mirroring
 * {@code SubscriptionExpiryDisabledIntegrationTest} for the billing sweep.
 *
 * <p>The property has to remove the trigger, not merely slow it: {@code @Scheduled(fixedRate)}
 * declares no initial delay, so the first tick fires as soon as the context is up regardless of
 * {@code auth.outbox.reconcile-rate-ms}. That tick calls the void
 * {@code TokenBlacklistPort.writeHeartbeat()}, and since Mockito's ongoing-stubbing state is
 * per-mock rather than per-thread, a scheduler thread landing between a test's own invocation on
 * that {@code @MockBean} and the {@code when(...)} wrapping it makes Mockito believe the void
 * method is the one being stubbed — surfacing as {@code CannotStubVoidMethodWithReturnValue} in a
 * {@code @BeforeEach} that never mentioned the reconciler.</p>
 *
 * <p>A separate top-level class rather than a {@code @Nested} one, for the same context-cache-key
 * reason the billing equivalent documents.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@Testcontainers
class OutboxBlacklistReconcilerToggleIntegrationTest {

    private static final String HMAC_SECRET = "integration-test-reconciler-toggle-secret";
    private static final String MERCHANT_ACCOUNT_ID = "merchant-reconciler-toggle";

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
    }

    @Autowired private ApplicationContext context;

    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private RedisTemplate<String, String> redisTemplate;
    @MockBean private LoginRateLimitPort loginRateLimitPort;

    @BeforeEach
    void setUp() {
        when(tokenBlacklistPort.isBlacklisted(anyString())).thenReturn(false);
        when(authDegradedGuard.isDegraded()).thenReturn(false);
    }

    /**
     * The integration-test profile disables the trigger for every test in this source set, so
     * this assertion also proves the profile-wide switch is actually wired — not just the
     * annotation.
     */
    @Test
    void the_trigger_bean_is_absent_under_the_integration_test_profile() {
        assertThat(context.getBeansOfType(OutboxBlacklistReconcilerTrigger.class)).isEmpty();
    }

    /**
     * The other half of the fix, and the reason the schedule had to be split off rather than the
     * whole component gated: {@code AuthRevocationIntegrationTest} autowires the reconciler and
     * drives {@code processBatch()} by hand. Gating the worker too would fail its context
     * startup outright.
     */
    @Test
    void the_reconciler_worker_still_exists_when_the_trigger_is_disabled() {
        assertThat(context.getBeansOfType(OutboxBlacklistReconciler.class)).hasSize(1);
    }
}
