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
import com.menta.shared.billing.CourseAccessSnapshot;
import com.menta.shared.billing.VirtualCourseEntitlementPort;
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
 * End-to-end coverage for US-BILLING-012 (#131) Phase 4: the admin trial-grant route, over the
 * real {@code SecurityConfig} filter chain, MySQL persistence, and the real D8 {@code
 * UserExistencePort} bean — no mock of it anywhere in this class.
 *
 * <p>Reuses {@code SubscriptionCancellationIntegrationTest}'s checkout+webhook and admin-cancel
 * fixtures to reach a genuinely {@code ACTIVE} paid subscription and a genuinely {@code
 * CANCELLED} trial, rather than inserting either directly.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class SubscriptionTrialIntegrationTest {

    private static final String HMAC_SECRET = "integration-test-trial-secret";
    private static final String MERCHANT_ACCOUNT_ID = "merchant-trial";
    private static final BigDecimal PLAN_PRICE = new BigDecimal("15000.00");
    private static final String COURSE_ID = "course-1";

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
    @Autowired private VirtualCourseEntitlementPort virtualCourseEntitlementPort;

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
        planCourseRepository.save(new PlanCourseJpaEntity(planId, COURSE_ID));
        return planId;
    }

    private UUID seedUser(Role role) {
        User user = User.create(Email.of("trial-" + UUID.randomUUID() + "@example.com"), "irrelevant-hash", role);
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
    private ResponseEntity<Map> assignTrial(UUID adminId, String userId, String planId, String reason, Object days) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("planId", planId);
        body.put("reason", reason);
        if (days != null) {
            body.put("days", days);
        }
        return http.exchange(
            "/api/v1/admin/billing/subscriptions/trial", HttpMethod.POST,
            new HttpEntity<>(body, headersFor(adminId, Role.ADMIN)), Map.class
        );
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> checkoutAndActivateFor(UUID userId, UUID planId) {
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
        return checkout;
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
    private ResponseEntity<Map> cancelAsAdmin(UUID adminId, UUID subscriptionId, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", reason);
        return http.exchange(
            "/api/v1/admin/billing/subscriptions/" + subscriptionId, HttpMethod.DELETE,
            new HttpEntity<>(body, headersFor(adminId, Role.ADMIN)), Map.class
        );
    }

    /** Simulates the Phase 5 sweep's effect directly at the persistence level — the sweep itself ships later. */
    private void simulateExpiry(UUID subscriptionId) {
        SubscriptionJpaEntity current = subscriptionRepository.findById(subscriptionId).orElseThrow();
        SubscriptionJpaEntity expired = new SubscriptionJpaEntity(
            current.getId(), current.getPaymentId(), current.getUserId(), current.getPlanId(),
            current.getIdempotencyKey(), null, "EXPIRED", current.getFulfillmentStatus(),
            current.getStartDate(), Instant.now().minusSeconds(60), current.getProviderPreferenceId(),
            current.getCheckoutUrl(), current.getCreatedAt(), current.getCancelledAt(), current.getCancelledBy(),
            current.getCancellationReason(), current.getType(), current.getGrantedAt(), current.getGrantedBy(),
            current.getGrantReason(), current.getGrantDays(), current.getVersion()
        );
        subscriptionRepository.save(expired);
    }

    // --- Escenario "Reject a trial grant for a nonexistent user" [S9] -------

    @Test
    void an_unknown_user_id_returns_404_and_creates_no_subscription() {
        UUID adminId = seedUser(Role.ADMIN);
        UUID planId = seedPlan();

        ResponseEntity<Map> response =
            assignTrial(adminId, UUID.randomUUID().toString(), planId.toString(), "motivo", 7);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(subscriptionRepository.findAll()).isEmpty();
    }

    // --- Escenario "Admin grants a trial subscription" [S1, A12] ------------

    @Test
    @SuppressWarnings("unchecked")
    void a_trial_grant_persists_the_full_course_snapshot_with_no_payment_and_no_provider_charge() {
        UUID adminId = seedUser(Role.ADMIN);
        UUID studentId = seedUser(Role.STUDENT);
        UUID planId = seedPlan();

        ResponseEntity<Map> response = assignTrial(adminId, studentId.toString(), planId.toString(), "motivo", 7);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("type")).isEqualTo("TRIAL");
        assertThat(response.getBody().get("status")).isEqualTo("ACTIVE");
        assertThat(response.getBody().get("days")).isEqualTo(7);

        UUID subscriptionId = UUID.fromString((String) response.getBody().get("subscriptionId"));
        SubscriptionJpaEntity entity = subscriptionRepository.findById(subscriptionId).orElseThrow();
        assertThat(entity.getPaymentId()).isNull();
        assertThat(paymentRepository.count()).isZero();
        assertThat(subscriptionCourseRepository.findBySubscriptionId(subscriptionId))
            .extracting("courseId")
            .containsExactly(COURSE_ID);
    }

    // --- Escenario "Missing reason is rejected" [S2] -------------------------

    @Test
    void a_blank_reason_is_rejected_with_400_and_creates_nothing() {
        UUID adminId = seedUser(Role.ADMIN);
        UUID studentId = seedUser(Role.STUDENT);
        UUID planId = seedPlan();

        ResponseEntity<Map> response = assignTrial(adminId, studentId.toString(), planId.toString(), "   ", 7);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(subscriptionRepository.findAll()).isEmpty();
    }

    // --- Escenario "A non-positive or absent days value is rejected" [S3] ---

    @Test
    void a_non_positive_days_value_is_rejected_with_400_and_creates_nothing() {
        UUID adminId = seedUser(Role.ADMIN);
        UUID studentId = seedUser(Role.STUDENT);
        UUID planId = seedPlan();

        ResponseEntity<Map> response = assignTrial(adminId, studentId.toString(), planId.toString(), "motivo", 0);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(subscriptionRepository.findAll()).isEmpty();
    }

    // --- Escenario "Trial and paid produce identical access decisions" [S5] -

    @Test
    void trial_and_paid_subscriptions_produce_identical_virtual_access_decisions() {
        UUID adminId = seedUser(Role.ADMIN);
        UUID trialUserId = seedUser(Role.STUDENT);
        UUID paidUserId = seedUser(Role.STUDENT);
        UUID planId = seedPlan();

        assertThat(assignTrial(adminId, trialUserId.toString(), planId.toString(), "motivo", 7).getStatusCode())
            .isEqualTo(HttpStatus.CREATED);
        assertThat(checkoutAndActivateFor(paidUserId, planId).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        CourseAccessSnapshot trialAccess = virtualCourseEntitlementPort.resolveCourseAccess(trialUserId, COURSE_ID);
        CourseAccessSnapshot paidAccess = virtualCourseEntitlementPort.resolveCourseAccess(paidUserId, COURSE_ID);

        assertThat(trialAccess).isEqualTo(paidAccess);
        assertThat(trialAccess.currentEntitlement()).isTrue();
    }

    // --- Escenario "Target already has an active subscription of either type" [S11] --

    @Test
    void a_second_trial_grant_to_the_same_user_is_rejected_with_409() {
        UUID adminId = seedUser(Role.ADMIN);
        UUID studentId = seedUser(Role.STUDENT);
        UUID planId = seedPlan();
        assertThat(assignTrial(adminId, studentId.toString(), planId.toString(), "primer motivo", 7).getStatusCode())
            .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> second =
            assignTrial(adminId, studentId.toString(), planId.toString(), "segundo motivo", 5);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(subscriptionRepository.findAll()).hasSize(1);
    }

    // --- Escenario "Paid checkout succeeds after trial expiry or cancellation" [S12] --

    @Test
    @SuppressWarnings("unchecked")
    void a_student_can_repurchase_after_their_trial_is_cancelled() {
        UUID adminId = seedUser(Role.ADMIN);
        UUID studentId = seedUser(Role.STUDENT);
        UUID planId = seedPlan();
        ResponseEntity<Map> grant = assignTrial(adminId, studentId.toString(), planId.toString(), "motivo", 7);
        UUID trialSubscriptionId = UUID.fromString((String) grant.getBody().get("subscriptionId"));
        assertThat(cancelAsAdmin(adminId, trialSubscriptionId, "fin del período de prueba").getStatusCode())
            .isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> repurchase = checkout(studentId, planId, "idem-repurchase-" + UUID.randomUUID());

        assertThat(repurchase.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(subscriptionRepository.findAll()).hasSize(2);
        SubscriptionJpaEntity stillCancelledTrial = subscriptionRepository.findById(trialSubscriptionId).orElseThrow();
        assertThat(stillCancelledTrial.getStatus()).isEqualTo("CANCELLED");
        assertThat(stillCancelledTrial.getType()).isEqualTo("TRIAL");
        UUID newSubscriptionId = UUID.fromString((String) repurchase.getBody().get("subscriptionId"));
        assertThat(newSubscriptionId).isNotEqualTo(trialSubscriptionId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void a_student_can_repurchase_after_their_trial_expires() {
        UUID adminId = seedUser(Role.ADMIN);
        UUID studentId = seedUser(Role.STUDENT);
        UUID planId = seedPlan();
        ResponseEntity<Map> grant = assignTrial(adminId, studentId.toString(), planId.toString(), "motivo", 7);
        UUID trialSubscriptionId = UUID.fromString((String) grant.getBody().get("subscriptionId"));
        simulateExpiry(trialSubscriptionId);

        ResponseEntity<Map> repurchase = checkout(studentId, planId, "idem-repurchase-" + UUID.randomUUID());

        assertThat(repurchase.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(subscriptionRepository.findAll()).hasSize(2);
        SubscriptionJpaEntity stillExpiredTrial = subscriptionRepository.findById(trialSubscriptionId).orElseThrow();
        assertThat(stillExpiredTrial.getStatus()).isEqualTo("EXPIRED");
        UUID newSubscriptionId = UUID.fromString((String) repurchase.getBody().get("subscriptionId"));
        assertThat(newSubscriptionId).isNotEqualTo(trialSubscriptionId);
    }

    // --- Non-admin defense-in-depth, real security chain [S4] ---------------

    @Test
    void a_non_admin_cannot_grant_a_trial_via_the_admin_route() {
        UUID studentId = seedUser(Role.STUDENT);
        UUID targetId = seedUser(Role.STUDENT);
        UUID planId = seedPlan();

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/billing/subscriptions/trial", HttpMethod.POST,
            new HttpEntity<>(
                Map.of("userId", targetId.toString(), "planId", planId.toString(), "reason", "motivo", "days", 7),
                headersFor(studentId, Role.STUDENT)
            ),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(subscriptionRepository.findAll()).isEmpty();
    }
}
