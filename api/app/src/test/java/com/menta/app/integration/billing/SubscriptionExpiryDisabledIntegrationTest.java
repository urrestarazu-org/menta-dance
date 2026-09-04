package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.infrastructure.scheduling.SubscriptionExpiryReconciler;
import com.menta.billing.infrastructure.scheduling.SubscriptionExpiryWorker;
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
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves A16's off switch is real (US-BILLING-012, design A16/A6b): with {@code
 * billing.subscription.expiry.enabled=false}, {@link SubscriptionExpiryReconciler}'s {@code
 * @ConditionalOnProperty} on the class keeps the bean out of the context entirely — a plain
 * {@code BillingConfigurationTest} cannot observe this, since neither sweep class is
 * {@code @Bean}-declared there (A6b), so this lives in a real {@code @SpringBootTest}.
 *
 * <p>A separate top-level class rather than a {@code @Nested} one — Spring's context cache keys
 * on the merged configuration (including this class's own {@code @TestPropertySource}), so a
 * distinct class gives an unambiguous, independently cached context without relying on {@code
 * @Nested} + {@code @SpringBootTest} composition.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@TestPropertySource(properties = "billing.subscription.expiry.enabled=false")
@Testcontainers
class SubscriptionExpiryDisabledIntegrationTest {

    private static final String HMAC_SECRET = "integration-test-expiry-disabled-secret";
    private static final String MERCHANT_ACCOUNT_ID = "merchant-expiry-disabled";

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
        when(tokenBlacklistPort.currentTokenVersion(anyString())).thenReturn(java.util.OptionalLong.empty());
        when(authDegradedGuard.isDegraded()).thenReturn(false);
    }

    @Test
    void the_reconciler_bean_is_absent_when_expiry_is_disabled() {
        assertThat(context.getBeansOfType(SubscriptionExpiryReconciler.class)).isEmpty();
    }

    /** The worker carries no {@code @ConditionalOnProperty} of its own — only the job's trigger is gated. */
    @Test
    void the_worker_bean_still_exists_when_expiry_is_disabled() {
        assertThat(context.getBeansOfType(SubscriptionExpiryWorker.class)).hasSize(1);
    }
}
