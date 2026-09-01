package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
import com.menta.billing.application.dto.ProviderPaymentResult;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanPaymentMethodJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.SubscriptionJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.WebhookInboxJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanPaymentMethodJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.WebhookInboxJpaRepository;
import com.menta.billing.infrastructure.webhook.WebhookInboxStatus;
import com.menta.billing.infrastructure.webhook.WebhookVerificationWorker;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.shared.domain.vo.Email;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end coverage for US-BILLING-011 (#130): self-service and admin subscription
 * cancellation, over the real {@code SecurityConfig} filter chain and MySQL persistence.
 *
 * <p>Reuses {@code SubscriptionCheckoutIntegrationTest}'s checkout+webhook fixture to reach a
 * genuinely {@code ACTIVE} subscription rather than inserting one directly — cancellation
 * behavior is only meaningful against the state the real checkout flow produces.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class SubscriptionCancellationIntegrationTest {

    private static final String HMAC_SECRET = "integration-test-cancellation-secret";
    private static final String MERCHANT_ACCOUNT_ID = "merchant-cancellation";
    private static final BigDecimal PLAN_PRICE = new BigDecimal("15000.00");

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

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private PlanJpaRepository planRepository;
    @Autowired private PlanCourseJpaRepository planCourseRepository;
    @Autowired private PlanPaymentMethodJpaRepository planPaymentMethodRepository;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private SubscriptionJpaRepository subscriptionRepository;
    @Autowired private SubscriptionCourseJpaRepository subscriptionCourseRepository;
    @Autowired private WebhookInboxJpaRepository inboxRepository;
    @Autowired private WebhookVerificationWorker worker;

    @MockBean private PaymentPreferencePort paymentPreferencePort;
    @MockBean private PaymentProviderPort paymentProviderPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;
    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockBean private ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;

    private final AtomicInteger preferenceSequence = new AtomicInteger();

    @BeforeEach
    void stubTheProviderPreference() {
        when(tokenBlacklistPort.isBlacklisted(anyString())).thenReturn(false);
        when(tokenBlacklistPort.currentTokenVersion(anyString())).thenReturn(java.util.OptionalLong.empty());
        when(paymentPreferencePort.createPreference(any())).thenAnswer(invocation -> {
            String preferenceId = "pref-" + preferenceSequence.incrementAndGet();
            return new PaymentPreferenceResult(preferenceId, "https://mp.example/checkout/" + preferenceId);
        });
    }

    @AfterEach
    void cleanUp() {
        inboxRepository.deleteAll();
        subscriptionCourseRepository.deleteAll();
        subscriptionRepository.deleteAll();
        paymentRepository.deleteAll();
        planCourseRepository.deleteAll();
        planPaymentMethodRepository.deleteAll();
        planRepository.deleteAll();
    }

    // --- fixtures -----------------------------------------------------------

    private UUID seedPlan() {
        UUID planId = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(new PlanJpaEntity(
            planId, "Plan Mensual", "Acceso mensual", PLAN_PRICE, "ARS", 30, false, PlanStatus.ACTIVE,
            "Términos", "Política de cancelación de prueba", now, now
        ));
        planPaymentMethodRepository.save(new PlanPaymentMethodJpaEntity(planId, "MERCADO_PAGO"));
        planCourseRepository.save(new PlanCourseJpaEntity(planId, "course-1"));
        return planId;
    }

    private UUID seedUser(Role role) {
        User user = User.create(Email.of("user-" + UUID.randomUUID() + "@example.com"), "irrelevant-hash", role);
        userRepository.save(user);
        return user.getId().getValue();
    }

    private HttpHeaders headersFor(UUID userId, Role role) {
        User user = new User(
            UserId.of(userId), Email.of("token-" + UUID.randomUUID() + "@example.com"), "hash", role,
            UserStatus.ACTIVE, java.time.LocalDateTime.now(), java.time.LocalDateTime.now()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessTokenIssuer.issue(user).token());
        return headers;
    }

    @SuppressWarnings("rawtypes")
    private UUID checkoutAndActivate(UUID userId, UUID planId) {
        Map<String, Object> body = new HashMap<>();
        body.put("planId", planId.toString());
        body.put("paymentMethod", "MERCADO_PAGO");
        body.put("idempotencyKey", "idem-" + UUID.randomUUID());
        ResponseEntity<Map> checkout = http.exchange(
            "/api/v1/billing/subscriptions", HttpMethod.POST,
            new HttpEntity<>(body, headersFor(userId, Role.STUDENT)), Map.class
        );
        String externalReference = (String) checkout.getBody().get("externalReference");
        String providerPaymentId = "mp-" + UUID.randomUUID();
        when(paymentProviderPort.fetchPayment(providerPaymentId)).thenReturn(new ProviderPaymentResult(
            "approved", Money.of(PLAN_PRICE, "ARS"), externalReference, MERCHANT_ACCOUNT_ID
        ));
        WebhookInboxJpaEntity row = new WebhookInboxJpaEntity(
            providerPaymentId + ":req-1", providerPaymentId, "req-1", WebhookInboxStatus.RECEIVED, 0, null, null,
            Instant.now(), null
        );
        inboxRepository.save(row);
        worker.process(row);
        return subscriptionRepository.findAll().stream()
            .filter(entity -> entity.getUserId().equals(userId))
            .findFirst().orElseThrow().getId();
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> checkout(UUID userId, UUID planId, String idempotencyKey) {
        Map<String, Object> body = new HashMap<>();
        body.put("planId", planId.toString());
        body.put("paymentMethod", "MERCADO_PAGO");
        body.put("idempotencyKey", idempotencyKey);
        return http.exchange(
            "/api/v1/billing/subscriptions", HttpMethod.POST,
            new HttpEntity<>(body, headersFor(userId, Role.STUDENT)), Map.class
        );
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> cancelOwn(UUID userId) {
        return http.exchange(
            "/api/v1/billing/subscriptions/me", HttpMethod.DELETE, new HttpEntity<>(headersFor(userId, Role.STUDENT)),
            Map.class
        );
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> cancelAsAdmin(UUID adminId, UUID subscriptionId, String reason) {
        return cancelViaAdminRoute(adminId, Role.ADMIN, subscriptionId, reason);
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> cancelViaAdminRoute(UUID callerId, Role role, UUID subscriptionId, String reason) {
        Map<String, Object> body = new HashMap<>();
        if (reason != null) {
            body.put("reason", reason);
        }
        return http.exchange(
            "/api/v1/admin/billing/subscriptions/" + subscriptionId, HttpMethod.DELETE,
            new HttpEntity<>(body, headersFor(callerId, role)), Map.class
        );
    }

    // --- Escenario 1 ----------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void a_student_cancels_their_own_active_subscription_without_moving_the_end_date() {
        UUID userId = seedUser(Role.STUDENT);
        UUID planId = seedPlan();
        checkoutAndActivate(userId, planId);
        SubscriptionJpaEntity before = subscriptionRepository.findAll().get(0);

        ResponseEntity<Map> response = cancelOwn(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("CANCELLED");
        assertThat(response.getBody().get("cancellationPolicy")).isEqualTo("Política de cancelación de prueba");
        assertThat(response.getBody()).doesNotContainKey("cancellationReason");

        SubscriptionJpaEntity after = subscriptionRepository.findAll().get(0);
        assertThat(after.getStatus()).isEqualTo("CANCELLED");
        assertThat(after.getEndDate()).isEqualTo(before.getEndDate());
        assertThat(after.getCancelledBy()).isEqualTo(userId);
        assertThat(after.getCancelledAt()).isNotNull();
    }

    // --- Escenario 2 ----------------------------------------------------------

    @Test
    void a_student_with_no_cancellable_subscription_gets_404_and_nothing_changes() {
        UUID userId = seedUser(Role.STUDENT);

        ResponseEntity<Map> response = cancelOwn(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(subscriptionRepository.findAll()).isEmpty();
    }

    // --- Escenario 8 ------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void an_admin_cancels_with_a_reason_and_it_is_persisted_but_never_returned() {
        UUID userId = seedUser(Role.STUDENT);
        UUID adminId = seedUser(Role.ADMIN);
        UUID planId = seedPlan();
        UUID subscriptionId = checkoutAndActivate(userId, planId);

        ResponseEntity<Map> response = cancelAsAdmin(adminId, subscriptionId, "reembolso por reclamo del cliente");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContainKey("cancellationReason");

        SubscriptionJpaEntity after = subscriptionRepository.findAll().get(0);
        assertThat(after.getStatus()).isEqualTo("CANCELLED");
        assertThat(after.getCancelledBy()).isEqualTo(adminId);
        assertThat(after.getCancellationReason()).isEqualTo("reembolso por reclamo del cliente");
    }

    // --- Escenario 9 ------------------------------------------------------------

    @Test
    void an_admin_cancellation_with_no_reason_is_rejected_and_changes_nothing() {
        UUID userId = seedUser(Role.STUDENT);
        UUID adminId = seedUser(Role.ADMIN);
        UUID planId = seedPlan();
        UUID subscriptionId = checkoutAndActivate(userId, planId);

        ResponseEntity<Map> response = cancelAsAdmin(adminId, subscriptionId, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        SubscriptionJpaEntity unchanged = subscriptionRepository.findAll().get(0);
        assertThat(unchanged.getStatus()).isEqualTo("ACTIVE");
        assertThat(unchanged.getCancelledAt()).isNull();
    }

    // --- Escenario 11 -----------------------------------------------------------

    @Test
    void a_non_admin_cannot_cancel_via_the_admin_route() {
        UUID userId = seedUser(Role.STUDENT);
        UUID otherStudentId = seedUser(Role.STUDENT);
        UUID planId = seedPlan();
        UUID subscriptionId = checkoutAndActivate(userId, planId);

        ResponseEntity<Map> response =
            cancelViaAdminRoute(otherStudentId, Role.STUDENT, subscriptionId, "motivo cualquiera");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        SubscriptionJpaEntity unchanged = subscriptionRepository.findAll().get(0);
        assertThat(unchanged.getStatus()).isEqualTo("ACTIVE");
    }

    // --- D3 / Escenario 4: re-purchase after cancellation, overlap notice -----

    /**
     * S4 + S5 end-to-end: cancelling still leaves paid access until {@code endDate}, so a
     * re-purchase of the same plan must succeed (never blocked) and carry the D3 notice — and
     * the old, still-live row must never be reactivated by the new checkout.
     */
    @Test
    @SuppressWarnings("unchecked")
    void a_repurchase_after_cancellation_with_remaining_access_carries_an_overlap_notice() {
        UUID userId = seedUser(Role.STUDENT);
        UUID planId = seedPlan();
        UUID cancelledSubscriptionId = checkoutAndActivate(userId, planId);
        assertThat(cancelOwn(userId).getStatusCode()).isEqualTo(HttpStatus.OK);
        SubscriptionJpaEntity cancelledSubscription =
            subscriptionRepository.findById(cancelledSubscriptionId).orElseThrow();

        ResponseEntity<Map> repurchase = checkout(userId, planId, "idem-repurchase-" + UUID.randomUUID());

        assertThat(repurchase.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> overlapNotice = (Map<String, Object>) repurchase.getBody().get("overlapNotice");
        assertThat(overlapNotice).isNotNull();
        assertThat(overlapNotice.get("code")).isEqualTo("OVERLAPPING_PAID_PERIOD");
        assertThat(Instant.parse((String) overlapNotice.get("currentAccessEndsAt")))
            .isEqualTo(cancelledSubscription.getEndDate());

        assertThat(subscriptionRepository.findAll()).hasSize(2);
        SubscriptionJpaEntity stillCancelled = subscriptionRepository.findById(cancelledSubscriptionId).orElseThrow();
        assertThat(stillCancelled.getStatus()).isEqualTo("CANCELLED");
        assertThat(stillCancelled.getActiveUserId()).isNull();
    }

    /** S6: no prior cancellation for this plan means the checkout reports no notice at all. */
    @Test
    @SuppressWarnings("unchecked")
    void a_first_time_checkout_with_no_prior_cancellation_reports_no_overlap_notice() {
        UUID userId = seedUser(Role.STUDENT);
        UUID planId = seedPlan();

        ResponseEntity<Map> response = checkout(userId, planId, "idem-" + UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("overlapNotice");
        assertThat(response.getBody().get("overlapNotice")).isNull();
    }
}
