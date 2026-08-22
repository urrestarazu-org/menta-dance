package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import com.menta.shared.domain.vo.Email;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
 * End-to-end coverage for US-BILLING-010: checkout → signed webhook →
 * {@code ACTIVE} subscription with vigencia and course snapshot.
 *
 * <p>MySQL via Testcontainers rather than the plain {@code test} (H2) profile
 * — the concurrency test here depends on a real unique index rejecting the
 * losing insert, which is exactly the behaviour an in-memory stand-in would
 * let us get wrong without noticing.</p>
 *
 * <p>The provider ports are mocked: {@code PaymentPreferencePort} is the only
 * call that creates an external charge, and {@code PaymentProviderPort} is the
 * authenticated read the webhook flow verifies against. The scheduler cadence
 * is pushed far out so the real reconciler never races these tests.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class SubscriptionCheckoutIntegrationTest {

    private static final String HMAC_SECRET = "integration-test-webhook-secret";
    private static final String MERCHANT_ACCOUNT_ID = "merchant-integration";
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

    private UUID seedPlan(PlanStatus status, String paymentMethod, String... courseIds) {
        UUID planId = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(new PlanJpaEntity(
            planId, "Plan Mensual", "Acceso mensual", PLAN_PRICE, "ARS", 30, false, status,
            "Términos", "Política", now, now
        ));
        planPaymentMethodRepository.save(new PlanPaymentMethodJpaEntity(planId, paymentMethod));
        for (String courseId : courseIds) {
            planCourseRepository.save(new PlanCourseJpaEntity(planId, courseId));
        }
        return planId;
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

    private static Map<String, Object> checkoutBody(UUID planId, String paymentMethod, String idempotencyKey) {
        Map<String, Object> body = new HashMap<>();
        body.put("planId", planId.toString());
        body.put("paymentMethod", paymentMethod);
        body.put("idempotencyKey", idempotencyKey);
        return body;
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> checkout(UUID userId, UUID planId, String paymentMethod, String idempotencyKey) {
        return http.exchange(
            "/api/v1/billing/subscriptions", HttpMethod.POST,
            new HttpEntity<>(checkoutBody(planId, paymentMethod, idempotencyKey), headersFor(userId)),
            Map.class
        );
    }

    /**
     * Drives the webhook half of the circuit. The provider response is what
     * carries the verified {@code external_reference} — the webhook row itself
     * only names a {@code data.id}, exactly as in production.
     */
    private void deliverWebhook(String providerPaymentId, String externalReference, String providerStatus) {
        when(paymentProviderPort.fetchPayment(providerPaymentId)).thenReturn(new ProviderPaymentResult(
            providerStatus, Money.of(PLAN_PRICE, "ARS"), externalReference, MERCHANT_ACCOUNT_ID
        ));
        WebhookInboxJpaEntity row = new WebhookInboxJpaEntity(
            providerPaymentId + ":req-1", providerPaymentId, "req-1", WebhookInboxStatus.RECEIVED,
            0, null, null, Instant.now(), null
        );
        inboxRepository.save(row);
        worker.process(row);
    }

    // --- Escenario 1 --------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void checkout_creates_a_pending_subscription_and_an_unbound_payment_then_returns_the_checkout_url() {
        UUID userId = seedStudent();
        UUID planId = seedPlan(PlanStatus.ACTIVE, "MERCADO_PAGO", "course-1");

        ResponseEntity<Map> response = checkout(userId, planId, "MERCADO_PAGO", "idem-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status")).isEqualTo("PENDING");
        assertThat(response.getBody().get("checkoutUrl")).isEqualTo("https://mp.example/checkout/pref-1");
        assertThat(response.getBody().get("providerPreferenceId")).isEqualTo("pref-1");

        assertThat(paymentRepository.findAll()).hasSize(1);
        var payment = paymentRepository.findAll().get(0);
        assertThat(payment.getUserId()).isEqualTo(userId);
        assertThat(payment.getStatusType()).isEqualTo("AWAITING_PROVIDER");
        // The provider id genuinely does not exist yet — that is the whole point.
        assertThat(payment.getProviderPaymentId()).isNull();
        assertThat(payment.getExpectedExternalReference()).isEqualTo(response.getBody().get("externalReference"));
        assertThat(payment.getTargetReference()).isEqualTo(planId.toString());

        assertThat(subscriptionRepository.findAll()).hasSize(1);
        SubscriptionJpaEntity subscription = subscriptionRepository.findAll().get(0);
        assertThat(subscription.getStatus()).isEqualTo("PENDING");
        assertThat(subscription.getUserId()).isEqualTo(userId);
        assertThat(subscription.getPlanId()).isEqualTo(planId);
        assertThat(subscription.getStartDate()).isNull();
        assertThat(subscriptionCourseRepository.findAll()).isEmpty();
    }

    @Test
    void checkout_without_a_token_is_rejected() {
        UUID planId = seedPlan(PlanStatus.ACTIVE, "MERCADO_PAGO", "course-1");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/subscriptions", HttpMethod.POST,
            new HttpEntity<>(checkoutBody(planId, "MERCADO_PAGO", "idem-1"), headers), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    // --- Escenario 2: the full circuit --------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void a_confirmed_payment_activates_the_subscription_with_vigencia_and_its_course_snapshot() {
        UUID userId = seedStudent();
        UUID planId = seedPlan(PlanStatus.ACTIVE, "MERCADO_PAGO", "course-1", "course-2");
        ResponseEntity<Map> checkout = checkout(userId, planId, "MERCADO_PAGO", "idem-1");
        String externalReference = (String) checkout.getBody().get("externalReference");

        deliverWebhook("mp-approved", externalReference, "approved");

        var payment = paymentRepository.findAll().get(0);
        assertThat(payment.getStatusType()).isEqualTo("COMPLETED");
        // Bound only now, and only after the provider's own response matched.
        assertThat(payment.getProviderPaymentId()).isEqualTo("mp-approved");

        SubscriptionJpaEntity subscription = subscriptionRepository.findAll().get(0);
        assertThat(subscription.getStatus()).isEqualTo("ACTIVE");
        assertThat(subscription.getFulfillmentStatus()).isEqualTo("ASSIGNED");
        assertThat(subscription.getStartDate()).isEqualTo(payment.getStatusChangedAt());
        assertThat(subscription.getEndDate())
            .isEqualTo(subscription.getStartDate().plus(30, ChronoUnit.DAYS));
        assertThat(subscriptionCourseRepository.findBySubscriptionId(subscription.getId()))
            .extracting(entity -> entity.getCourseId())
            .containsExactlyInAnyOrder("course-1", "course-2");
    }

    /** Escenario 2b: the snapshot is frozen, so a later plan edit cannot reach a subscription already paid. */
    @Test
    @SuppressWarnings("unchecked")
    void an_administrative_change_to_the_plan_never_alters_an_already_active_subscription() {
        UUID userId = seedStudent();
        UUID planId = seedPlan(PlanStatus.ACTIVE, "MERCADO_PAGO", "course-1", "course-2");
        ResponseEntity<Map> checkout = checkout(userId, planId, "MERCADO_PAGO", "idem-1");
        deliverWebhook("mp-approved", (String) checkout.getBody().get("externalReference"), "approved");
        UUID subscriptionId = subscriptionRepository.findAll().get(0).getId();

        // Admin removes a course from the plan and deactivates it.
        planCourseRepository.deleteAll(planCourseRepository.findByPlanId(planId).stream()
            .filter(course -> course.getCourseId().equals("course-2")).toList());
        Instant now = Instant.now();
        planRepository.save(new PlanJpaEntity(
            planId, "Plan Mensual", "Acceso mensual", PLAN_PRICE, "ARS", 30, false, PlanStatus.INACTIVE,
            "Términos", "Política", now, now
        ));

        assertThat(subscriptionCourseRepository.findBySubscriptionId(subscriptionId))
            .extracting(entity -> entity.getCourseId())
            .containsExactlyInAnyOrder("course-1", "course-2");
    }

    /** Replay: the same webhook processed twice must not duplicate fulfillment or move the vigencia. */
    @Test
    @SuppressWarnings("unchecked")
    void a_replayed_webhook_never_duplicates_fulfillment() {
        UUID userId = seedStudent();
        UUID planId = seedPlan(PlanStatus.ACTIVE, "MERCADO_PAGO", "course-1");
        ResponseEntity<Map> checkout = checkout(userId, planId, "MERCADO_PAGO", "idem-1");
        String externalReference = (String) checkout.getBody().get("externalReference");

        deliverWebhook("mp-replay", externalReference, "approved");
        Instant firstStartDate = subscriptionRepository.findAll().get(0).getStartDate();
        inboxRepository.deleteAll();
        deliverWebhook("mp-replay", externalReference, "approved");

        assertThat(subscriptionRepository.findAll()).hasSize(1);
        assertThat(subscriptionRepository.findAll().get(0).getStartDate()).isEqualTo(firstStartDate);
        assertThat(subscriptionCourseRepository.findAll()).hasSize(1);
    }

    // --- Escenario 3 --------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void a_second_checkout_is_rejected_with_409_and_starts_no_provider_charge() {
        UUID userId = seedStudent();
        UUID planId = seedPlan(PlanStatus.ACTIVE, "MERCADO_PAGO", "course-1");
        checkout(userId, planId, "MERCADO_PAGO", "idem-1");

        ResponseEntity<Map> second = checkout(userId, planId, "MERCADO_PAGO", "idem-2");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("code")).isEqualTo("SUBSCRIPTION_ALREADY_ACTIVE");
        assertThat(paymentRepository.findAll()).hasSize(1);
        assertThat(subscriptionRepository.findAll()).hasSize(1);
        verify(paymentPreferencePort, times(1)).createPreference(any());
    }

    /**
     * The unique index — not the lookup that precedes it — is the actual
     * guarantee. Two simultaneous checkouts must resolve to exactly one
     * subscription, one payment and one provider charge.
     */
    @Test
    void two_simultaneous_checkouts_for_the_same_user_produce_exactly_one_subscription()
        throws InterruptedException {
        UUID userId = seedStudent();
        UUID planId = seedPlan(PlanStatus.ACTIVE, "MERCADO_PAGO", "course-1");
        int concurrency = 4;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrency);
        AtomicInteger created = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);

        for (int i = 0; i < concurrency; i++) {
            String idempotencyKey = "idem-concurrent-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    if (checkout(userId, planId, "MERCADO_PAGO", idempotencyKey).getStatusCode()
                        == HttpStatus.CREATED) {
                        created.incrementAndGet();
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(created.get()).isEqualTo(1);
        assertThat(subscriptionRepository.findAll()).hasSize(1);
        // The losers rolled back with their Payment: no orphan rows.
        assertThat(paymentRepository.findAll()).hasSize(1);
        verify(paymentPreferencePort, times(1)).createPreference(any());
    }

    // --- Escenario 4 / 4b ---------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void an_inactive_plan_is_indistinguishable_from_a_missing_one_and_writes_nothing() {
        UUID userId = seedStudent();
        UUID inactivePlanId = seedPlan(PlanStatus.INACTIVE, "MERCADO_PAGO", "course-1");

        ResponseEntity<Map> inactive = checkout(userId, inactivePlanId, "MERCADO_PAGO", "idem-1");
        ResponseEntity<Map> missing = checkout(userId, UUID.randomUUID(), "MERCADO_PAGO", "idem-2");

        assertThat(inactive.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(inactive.getBody().get("code")).isEqualTo("PLAN_NOT_AVAILABLE");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(missing.getBody().get("code")).isEqualTo("PLAN_NOT_AVAILABLE");
        assertThat(paymentRepository.findAll()).isEmpty();
        assertThat(subscriptionRepository.findAll()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void a_payment_method_the_plan_does_not_accept_is_rejected_with_the_accepted_list() {
        UUID userId = seedStudent();
        UUID planId = seedPlan(PlanStatus.ACTIVE, "BANK_TRANSFER", "course-1");

        ResponseEntity<Map> response = checkout(userId, planId, "MERCADO_PAGO", "idem-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("code")).isEqualTo("PAYMENT_METHOD_NOT_ACCEPTED");
        assertThat((List<String>) response.getBody().get("acceptedPaymentMethods"))
            .containsExactly("BANK_TRANSFER");
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    // --- Escenario 5 --------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void replaying_the_idempotency_key_returns_the_same_ids_without_a_second_charge() {
        UUID userId = seedStudent();
        UUID planId = seedPlan(PlanStatus.ACTIVE, "MERCADO_PAGO", "course-1");

        ResponseEntity<Map> first = checkout(userId, planId, "MERCADO_PAGO", "idem-1");
        ResponseEntity<Map> replay = checkout(userId, planId, "MERCADO_PAGO", "idem-1");

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getBody().get("subscriptionId")).isEqualTo(first.getBody().get("subscriptionId"));
        assertThat(replay.getBody().get("paymentId")).isEqualTo(first.getBody().get("paymentId"));
        assertThat(replay.getBody().get("checkoutUrl")).isEqualTo(first.getBody().get("checkoutUrl"));
        assertThat(paymentRepository.findAll()).hasSize(1);
        assertThat(subscriptionRepository.findAll()).hasSize(1);
        verify(paymentPreferencePort, times(1)).createPreference(any());
    }

    // --- Escenario 6 --------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void a_rejected_payment_never_activates_and_frees_the_user_to_start_over() {
        UUID userId = seedStudent();
        UUID planId = seedPlan(PlanStatus.ACTIVE, "MERCADO_PAGO", "course-1");
        ResponseEntity<Map> checkout = checkout(userId, planId, "MERCADO_PAGO", "idem-1");

        deliverWebhook("mp-rejected", (String) checkout.getBody().get("externalReference"), "rejected");

        assertThat(paymentRepository.findAll().get(0).getStatusType()).isEqualTo("REJECTED");
        SubscriptionJpaEntity subscription = subscriptionRepository.findAll().get(0);
        assertThat(subscription.getStatus()).isEqualTo("CANCELLED");
        assertThat(subscription.getActiveUserId()).isNull();
        assertThat(subscriptionCourseRepository.findAll()).isEmpty();

        ResponseEntity<Map> retry = checkout(userId, planId, "MERCADO_PAGO", "idem-2");

        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(subscriptionRepository.findAll()).hasSize(2);
    }

    /** An external reference nobody knows never fabricates a Payment (US-BILLING-002). */
    @Test
    void an_unknown_external_reference_leaves_the_inbox_row_for_manual_reconciliation() {
        deliverWebhook("mp-orphan", "SUB-" + UUID.randomUUID(), "approved");

        assertThat(paymentRepository.findAll()).isEmpty();
        assertThat(subscriptionRepository.findAll()).isEmpty();
        assertThat(inboxRepository.findAll().get(0).getStatus())
            .isEqualTo(WebhookInboxStatus.RECONCILIATION_REQUIRED);
    }
}
