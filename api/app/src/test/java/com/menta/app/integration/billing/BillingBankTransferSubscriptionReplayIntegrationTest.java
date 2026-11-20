package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.app.integration.support.AbstractBillingMySqlIntegrationTest;
import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.billing.application.dto.PaymentPreferenceResult;
import com.menta.billing.application.dto.RateLimitDecision;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanPaymentMethodJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanPaymentMethodJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionJpaRepository;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.application.port.out.CheckInRateLimitPort;
import com.menta.shared.domain.vo.Email;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Replaying the {@code idempotencyKey} of a bank-transfer subscription checkout (#793).
 *
 * <p>MySQL via Testcontainers, not H2: the second scenario depends on the real {@code
 * uq_billing_subscriptions_idempotency} index and on the router's transaction boundary deciding
 * whether the {@code Payment} written before the colliding insert survives.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class BillingBankTransferSubscriptionReplayIntegrationTest extends AbstractBillingMySqlIntegrationTest {

    private static final BigDecimal PLAN_PRICE = new BigDecimal("15000.00");
    private static final String CBU = "0000003100000000000001";
    private static final String ALIAS = "menta.dance.replay";
    private static final String HOLDER = "Menta Dance SRL";
    private static final String CUIT = "30-00000000-1";

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("billing.webhook.mercadopago.hmac-secret", () -> "integration-test-bank-transfer-replay");
        registry.add("billing.webhook.reconcile-rate-ms", () -> "999999999");
        registry.add("billing.mercadopago.merchant-account-id", () -> "merchant-bank-transfer-replay");
        registry.add("billing.bank-transfer.account.cbu", () -> CBU);
        registry.add("billing.bank-transfer.account.alias", () -> ALIAS);
        registry.add("billing.bank-transfer.account.holder", () -> HOLDER);
        registry.add("billing.bank-transfer.account.cuit", () -> CUIT);
    }

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private PlanJpaRepository planRepository;
    @Autowired private PlanCourseJpaRepository planCourseRepository;
    @Autowired private PlanPaymentMethodJpaRepository planPaymentMethodRepository;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private SubscriptionJpaRepository subscriptionJpaRepository;
    @Autowired private SubscriptionCourseJpaRepository subscriptionCourseRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private PaymentPreferencePort paymentPreferencePort;
    @MockitoBean private PaymentProviderPort paymentProviderPort;
    @MockitoBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockitoBean private BankTransferRateLimitPort bankTransferRateLimitPort;
    @MockitoBean private CourseCatalogPort courseCatalogPort;
    @MockitoBean private AuthDegradedGuard authDegradedGuard;
    @MockitoBean private TokenBlacklistPort tokenBlacklistPort;
    @MockitoBean private LoginRateLimitPort loginRateLimitPort;
    @MockitoBean private ActivationRateLimitPort activationRateLimitPort;
    @MockitoBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockitoBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    // US-PHYSICAL-001: ProcessPhysicalCheckInUseCaseImpl needs a RedisTemplate
    // its bean factory would otherwise fail to resolve in this Redis-less context.
    @MockitoBean private ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;
    // ADR-0044: backed by a RedisTemplate this Redis-less context does not have.
    @MockitoBean private CheckInRateLimitPort checkInRateLimitPort;

    @BeforeEach
    void stubCollaborators() {
        when(tokenBlacklistPort.isBlacklisted(anyString())).thenReturn(false);
        when(tokenBlacklistPort.currentTokenVersion(anyString())).thenReturn(java.util.OptionalLong.empty());
        when(bankTransferRateLimitPort.consumeBankTransferCreation(any())).thenReturn(RateLimitDecision.allowed());
        when(paymentPreferencePort.createPreference(any()))
            .thenReturn(new PaymentPreferenceResult("pref-1", "https://mp.example/checkout/pref-1"));
    }

    @AfterEach
    void cleanUp() {
        subscriptionCourseRepository.deleteAll();
        subscriptionJpaRepository.deleteAll();
        paymentRepository.deleteAll();
        planCourseRepository.deleteAll();
        planPaymentMethodRepository.deleteAll();
        planRepository.deleteAll();
    }

    // --- fixtures -----------------------------------------------------------

    private UUID seedPlan(BigDecimal price, String... paymentMethods) {
        UUID planId = UUID.randomUUID();
        savePlan(planId, price);
        for (String paymentMethod : paymentMethods) {
            planPaymentMethodRepository.save(new PlanPaymentMethodJpaEntity(planId, paymentMethod));
        }
        planCourseRepository.save(new PlanCourseJpaEntity(planId, "course-1"));
        return planId;
    }

    private void savePlan(UUID planId, BigDecimal price) {
        Instant now = Instant.now();
        planRepository.save(new PlanJpaEntity(
            planId, "Plan Mensual", "Acceso mensual", price, "ARS", 30, false, PlanStatus.ACTIVE,
            "Términos", "Política", now, now
        ));
    }

    private UUID seedStudent() {
        User user = User.create(
            Email.of("student-" + UUID.randomUUID() + "@example.com"), "irrelevant-hash", Role.STUDENT
        );
        userRepository.save(user);
        return user.getId().getValue();
    }

    private HttpHeaders headersFor(UUID userId) {
        User user = new User(
            UserId.of(userId), Email.of("token@example.com"), "hash", Role.STUDENT, UserStatus.ACTIVE,
            java.time.LocalDateTime.now(), java.time.LocalDateTime.now()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessTokenIssuer.issue(user).token());
        return headers;
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> checkout(UUID userId, UUID planId, String paymentMethod, String idempotencyKey) {
        Map<String, Object> body = new HashMap<>();
        body.put("planId", planId.toString());
        body.put("paymentMethod", paymentMethod);
        body.put("idempotencyKey", idempotencyKey);
        return http.exchange(
            "/api/v1/billing/subscriptions", HttpMethod.POST, new HttpEntity<>(body, headersFor(userId)), Map.class
        );
    }

    /**
     * Releases the user's slot the way a payment that never settled does (US-BILLING-010
     * escenario 6, {@code Subscription#cancelled}) — the same transition the 72h expiry sweep and
     * an admin rejection of the transfer apply.
     */
    private void releaseTheSlotOf(UUID subscriptionId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            subscriptionRepository.save(subscriptionRepository.findById(subscriptionId).orElseThrow().cancelled())
        );
    }

    // --- (a) the original subscription still occupies the slot --------------

    @Test
    @SuppressWarnings("unchecked")
    void a_retry_while_the_subscription_is_pending_returns_the_original_instructions_without_charging_quota() {
        UUID userId = seedStudent();
        UUID planId = seedPlan(PLAN_PRICE, "BANK_TRANSFER");
        ResponseEntity<Map> first = checkout(userId, planId, "BANK_TRANSFER", "idem-bt-1");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> replay = checkout(userId, planId, "BANK_TRANSFER", "idem-bt-1");

        assertThat(replay.getStatusCode()).as("replay body: %s", replay.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getBody()).isEqualTo(first.getBody());
        Map<String, Object> instructions = (Map<String, Object>) replay.getBody().get("bankTransferInstructions");
        assertThat(instructions)
            .containsEntry("cbu", CBU).containsEntry("alias", ALIAS)
            .containsEntry("holder", HOLDER).containsEntry("cuit", CUIT)
            .containsEntry("reference", "SUB-" + first.getBody().get("paymentId"));
        assertThat(paymentRepository.findAll()).hasSize(1);
        assertThat(subscriptionJpaRepository.findAll()).hasSize(1);
        verify(bankTransferRateLimitPort, times(1)).consumeBankTransferCreation(userId);
    }

    /** The amount is the one the original payment expects, never the plan's current price. */
    @Test
    @SuppressWarnings("unchecked")
    void a_retry_after_the_plan_price_changed_still_quotes_the_original_payment_amount() {
        UUID userId = seedStudent();
        UUID planId = seedPlan(PLAN_PRICE, "BANK_TRANSFER");
        ResponseEntity<Map> first = checkout(userId, planId, "BANK_TRANSFER", "idem-bt-price");
        savePlan(planId, new BigDecimal("99999.00"));

        ResponseEntity<Map> replay = checkout(userId, planId, "BANK_TRANSFER", "idem-bt-price");

        assertThat(replay.getStatusCode()).as("replay body: %s", replay.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getBody().get("bankTransferInstructions"))
            .isEqualTo(first.getBody().get("bankTransferInstructions"));
        assertThat(paymentRepository.findAll().get(0).getExpectedAmount()).isEqualByComparingTo(PLAN_PRICE);
    }

    // --- (b) the slot was released, the key is still taken ------------------

    @Test
    @SuppressWarnings("unchecked")
    void a_retry_after_the_slot_was_released_returns_the_original_checkout_and_leaves_no_orphan_payment() {
        UUID userId = seedStudent();
        UUID planId = seedPlan(PLAN_PRICE, "BANK_TRANSFER");
        ResponseEntity<Map> first = checkout(userId, planId, "BANK_TRANSFER", "idem-bt-released");
        releaseTheSlotOf(UUID.fromString((String) first.getBody().get("subscriptionId")));

        ResponseEntity<Map> replay = checkout(userId, planId, "BANK_TRANSFER", "idem-bt-released");

        assertThat(replay.getStatusCode()).as("replay body: %s", replay.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getBody().get("subscriptionId")).isEqualTo(first.getBody().get("subscriptionId"));
        assertThat(replay.getBody().get("paymentId")).isEqualTo(first.getBody().get("paymentId"));
        // Like the Mercado Pago replay, the subscription comes back in its current state.
        assertThat(replay.getBody().get("status")).isEqualTo("CANCELLED");
        assertThat(replay.getBody().get("bankTransferInstructions"))
            .isEqualTo(first.getBody().get("bankTransferInstructions"));
        assertThat(paymentRepository.findAll()).as("payments after the retry").hasSize(1);
        assertThat(subscriptionJpaRepository.findAll()).hasSize(1);
        verify(bankTransferRateLimitPort, times(1)).consumeBankTransferCreation(userId);
    }

    // --- the same key reused across payment methods -------------------------

    /**
     * A key first used for Checkout Pro answers with that checkout — never with bank-transfer
     * instructions for a payment whose expected merchant is the Mercado Pago account.
     */
    @Test
    void a_bank_transfer_retry_of_a_mercado_pago_key_returns_the_original_checkout_pro_answer() {
        UUID userId = seedStudent();
        UUID planId = seedPlan(PLAN_PRICE, "MERCADO_PAGO", "BANK_TRANSFER");
        ResponseEntity<Map> first = checkout(userId, planId, "MERCADO_PAGO", "idem-cross");

        ResponseEntity<Map> replay = checkout(userId, planId, "BANK_TRANSFER", "idem-cross");

        assertThat(replay.getStatusCode()).as("replay body: %s", replay.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getBody().get("subscriptionId")).isEqualTo(first.getBody().get("subscriptionId"));
        assertThat(replay.getBody().get("checkoutUrl")).isEqualTo("https://mp.example/checkout/pref-1");
        assertThat(replay.getBody().get("bankTransferInstructions")).isNull();
        assertThat(paymentRepository.findAll()).hasSize(1);
        verify(bankTransferRateLimitPort, never()).consumeBankTransferCreation(any());
    }
}
