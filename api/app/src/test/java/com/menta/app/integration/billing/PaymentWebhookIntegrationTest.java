package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.application.dto.CreateSubscriptionCheckoutCommand;
import com.menta.billing.application.dto.PaymentPreferenceResult;
import com.menta.billing.application.dto.ProviderPaymentResult;
import com.menta.billing.application.port.in.CreateSubscriptionCheckoutUseCase;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.infrastructure.persistence.entity.PaymentJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanPaymentMethodJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.WebhookInboxJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanPaymentMethodJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.ReconciliationTaskJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.WebhookInboxJpaRepository;
import com.menta.billing.infrastructure.webhook.WebhookVerificationWorker;
import com.menta.app.outbox.OutboxReconciliationWorker;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * MySQL-backed HTTP + worker integration coverage for the Mercado Pago
 * webhook flow (US-BILLING-002). The scheduler's cadence is pushed far out
 * ({@code billing.webhook.reconcile-rate-ms}) so the real {@code
 * WebhookInboxReconciler} never races these tests — verification is driven
 * deterministically via the autowired {@code WebhookVerificationWorker}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class PaymentWebhookIntegrationTest {

    private static final String HMAC_SECRET = "integration-test-webhook-secret";

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
        registry.add("billing.webhook.max-attempts", () -> "2");
    }

    @Autowired private TestRestTemplate http;
    @Autowired private WebhookInboxJpaRepository inboxRepository;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private PlanJpaRepository planRepository;
    @Autowired private PlanCourseJpaRepository planCourseRepository;
    @Autowired private PlanPaymentMethodJpaRepository planPaymentMethodRepository;
    @Autowired private PurchaseJpaRepository purchaseRepository;
    @Autowired private ReconciliationTaskJpaRepository reconciliationTaskRepository;
    @Autowired private SubscriptionJpaRepository subscriptionRepository;
    @Autowired private SubscriptionCourseJpaRepository subscriptionCourseRepository;
    @Autowired private WebhookVerificationWorker worker;
    @Autowired private CreateSubscriptionCheckoutUseCase createSubscriptionCheckoutUseCase;
    @Autowired private OutboxRowJpaRepository outboxRepository;
    @Autowired private OutboxReconciliationWorker outboxWorker;
    @Autowired private PhysicalCapacityAssignmentJpaRepository physicalCapacityAssignmentRepository;
    @Autowired private PhysicalCourseJpaRepository physicalCourseRepository;
    @Autowired private PhysicalSessionJpaRepository physicalSessionRepository;

    @MockBean private PaymentProviderPort paymentProviderPort;
    @MockBean private PaymentPreferencePort paymentPreferencePort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;
    // US-PHYSICAL-001: ProcessPhysicalCheckInUseCaseImpl needs a RedisTemplate
    // its bean factory would otherwise fail to resolve in this Redis-less context.
    @MockBean private ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;
    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;

    @AfterEach
    void cleanUp() {
        reconciliationTaskRepository.deleteAll();
        purchaseRepository.deleteAll();
        subscriptionCourseRepository.deleteAll();
        subscriptionRepository.deleteAll();
        paymentRepository.deleteAll();
        planCourseRepository.deleteAll();
        planPaymentMethodRepository.deleteAll();
        planRepository.deleteAll();
        inboxRepository.deleteAll();
        outboxRepository.deleteAll();
        physicalCapacityAssignmentRepository.deleteAll();
        physicalSessionRepository.deleteAll();
        physicalCourseRepository.deleteAll();
    }

    private static String hmac(String manifest, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private ResponseEntity<String> postWebhook(String dataId, String requestId, String signatureHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-request-id", requestId);
        headers.set("x-signature", signatureHeader);
        return http.exchange(
            "/api/v1/billing/payments/mercadopago/webhook?data.id=" + dataId, HttpMethod.POST,
            new HttpEntity<>(headers), String.class
        );
    }

    private UUID seedPendingPhysicalPayment(String providerPaymentId, UUID studentId, UUID sessionId) {
        UUID id = UUID.randomUUID();
        paymentRepository.save(new PaymentJpaEntity(
            id, studentId, providerPaymentId, new BigDecimal("100.00"), "ARS",
            "ext-1", "merchant-1", "PHYSICAL", sessionId.toString(), "AWAITING_PROVIDER",
            null, null, Instant.now()
        ));
        return id;
    }

    private UUID seedActivePlanWithCourses(String... courseIds) {
        UUID planId = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(new PlanJpaEntity(
            planId, "Virtual integration plan", "Test plan", new BigDecimal("100.00"), "ARS", 30,
            false, PlanStatus.ACTIVE, "Terms", "Policy", now, now
        ));
        planPaymentMethodRepository.save(new PlanPaymentMethodJpaEntity(planId, "MERCADO_PAGO"));
        for (String courseId : courseIds) {
            planCourseRepository.save(new PlanCourseJpaEntity(planId, courseId));
        }
        return planId;
    }

    private UUID seedSession(int capacity) {
        UUID courseId = UUID.randomUUID();
        Instant now = Instant.now();
        physicalCourseRepository.save(new PhysicalCourseJpaEntity(
            courseId, "Integration course", "Test course", UUID.randomUUID(), "Test professor",
            "MONDAY", LocalTime.NOON, 60, "BEGINNER", capacity, CourseStatus.ACTIVE, now, now
        ));
        UUID sessionId = UUID.randomUUID();
        physicalSessionRepository.save(new PhysicalSessionJpaEntity(
            sessionId, courseId, now.plusSeconds(86_400), capacity, "SCHEDULED", null
        ));
        return sessionId;
    }

    private WebhookInboxJpaEntity receivedWebhookRow(String providerPaymentId, String requestId) {
        return inboxRepository.save(new WebhookInboxJpaEntity(
            providerPaymentId + ":" + requestId, providerPaymentId, requestId,
            com.menta.billing.infrastructure.webhook.WebhookInboxStatus.RECEIVED,
            0, null, null, Instant.now(), null
        ));
    }

    private void dispatchPendingOutboxEvent() {
        OutboxRowJpaEntity event = outboxRepository.findAll().getFirst();
        assertThat(outboxWorker.process(event)).isFalse();
    }

    @Test
    void an_invalid_signature_returns_401_and_never_touches_the_inbox() {
        ResponseEntity<String> response = postWebhook("mp-1", "req-1", "ts=1700000000,v1=deadbeef");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(inboxRepository.findAll()).isEmpty();
    }

    @Test
    void an_expired_timestamp_returns_401() {
        long staleTs = Instant.now().minusSeconds(600).getEpochSecond();
        String manifest = "id:mp-1;request-id:req-1;ts:" + staleTs + ";";
        String signature = "ts=" + staleTs + ",v1=" + hmac(manifest, HMAC_SECRET);

        ResponseEntity<String> response = postWebhook("mp-1", "req-1", signature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("WEBHOOK_TIMESTAMP_EXPIRED");
    }

    @Test
    void a_valid_webhook_is_persisted_and_a_replay_never_duplicates_the_inbox_row() {
        String dataId = "mp-replay";
        String requestId = "req-replay";
        long ts = Instant.now().getEpochSecond();
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        String signature = "ts=" + ts + ",v1=" + hmac(manifest, HMAC_SECRET);

        ResponseEntity<String> first = postWebhook(dataId, requestId, signature);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(inboxRepository.findAll()).hasSize(1);

        // Byte-identical replay: same dataId, requestId and signature.
        ResponseEntity<String> replay = postWebhook(dataId, requestId, signature);

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(inboxRepository.findAll()).hasSize(1);
    }

    @Test
    void the_worker_confirms_a_matching_physical_payment_without_creating_billing_fulfillment() {
        UUID sessionId = seedSession(2);
        UUID paymentId = seedPendingPhysicalPayment("mp-idempotent", UUID.randomUUID(), sessionId);
        when(paymentProviderPort.fetchPayment("mp-idempotent")).thenReturn(
            new ProviderPaymentResult("approved", Money.of(new BigDecimal("100.00"), "ARS"), "ext-1", "merchant-1")
        );
        WebhookInboxJpaEntity row = receivedWebhookRow("mp-idempotent", "req-1");

        worker.process(row);
        dispatchPendingOutboxEvent();

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatusType()).isEqualTo("COMPLETED");
        assertThat(purchaseRepository.findAll()).hasSize(1);
        assertThat(purchaseRepository.findByPaymentId(paymentId).orElseThrow().getStatus())
            .isEqualTo("PENDING_FULFILLMENT");
    }

    @Test
    void a_matching_approved_virtual_payment_activates_and_assigns_the_local_subscription() {
        UUID planId = seedActivePlanWithCourses("course-1", "course-2");
        UUID studentId = UUID.randomUUID();
        when(paymentPreferencePort.createPreference(any())).thenReturn(
            new PaymentPreferenceResult("pref-virtual", "https://mp.example/checkout/pref-virtual")
        );
        var checkout = createSubscriptionCheckoutUseCase.create(
            new CreateSubscriptionCheckoutCommand(studentId, planId.toString(), PaymentMethod.MERCADO_PAGO, "idem-virtual")
        );
        when(paymentProviderPort.fetchPayment("mp-virtual-approved")).thenReturn(
            new ProviderPaymentResult(
                "approved", Money.of(new BigDecimal("100.00"), "ARS"), checkout.externalReference(), ""
            )
        );

        worker.process(receivedWebhookRow("mp-virtual-approved", "req-virtual"));

        PaymentJpaEntity payment = paymentRepository.findAll().getFirst();
        var subscription = subscriptionRepository.findByPaymentId(payment.getId()).orElseThrow();
        assertThat(payment.getStatusType()).isEqualTo("COMPLETED");
        assertThat(subscription.getStatus()).isEqualTo("ACTIVE");
        assertThat(subscription.getFulfillmentStatus()).isEqualTo("ASSIGNED");
        assertThat(subscriptionCourseRepository.findBySubscriptionId(subscription.getId()))
            .extracting(course -> course.getCourseId())
            .containsExactlyInAnyOrder("course-1", "course-2");
        assertThat(purchaseRepository.findAll()).isEmpty();
        assertThat(outboxRepository.findAll()).isEmpty();
    }

    @Test
    void a_physical_payment_completion_leaves_capacity_orchestration_to_app() {
        UUID sessionId = seedSession(2);
        UUID studentId = UUID.randomUUID();
        seedPendingPhysicalPayment("mp-fulfillment-fails", studentId, sessionId);
        when(paymentProviderPort.fetchPayment("mp-fulfillment-fails")).thenReturn(
            new ProviderPaymentResult("approved", Money.of(new BigDecimal("100.00"), "ARS"), "ext-1", "merchant-1")
        );
        WebhookInboxJpaEntity row = receivedWebhookRow("mp-fulfillment-fails", "req-1");

        worker.process(row);
        dispatchPendingOutboxEvent();

        assertThat(paymentRepository.findAll()).allSatisfy(
            payment -> assertThat(payment.getStatusType()).isEqualTo("COMPLETED")
        );
        assertThat(purchaseRepository.findAll()).hasSize(1);
        assertThat(physicalCapacityAssignmentRepository.existsBySessionIdAndStudentId(sessionId, studentId)).isTrue();
    }

    @Test
    void webhook_redelivery_is_idempotent_and_does_not_create_a_second_purchase_row() {
        UUID sessionId = seedSession(2);
        UUID studentId = UUID.randomUUID();
        UUID paymentId = seedPendingPhysicalPayment("mp-idempotent", studentId, sessionId);
        when(paymentProviderPort.fetchPayment("mp-idempotent")).thenReturn(
            new ProviderPaymentResult("approved", Money.of(new BigDecimal("100.00"), "ARS"), "ext-1", "merchant-1")
        );
        WebhookInboxJpaEntity row = receivedWebhookRow("mp-idempotent", "req-1");

        worker.process(row);
        worker.process(row);
        dispatchPendingOutboxEvent();

        assertThat(purchaseRepository.findAll()).hasSize(1);
        assertThat(purchaseRepository.findByPaymentId(paymentId).orElseThrow().getStatus())
            .isEqualTo("PENDING_FULFILLMENT");
        assertThat(physicalCapacityAssignmentRepository.findAll()).hasSize(1);
        assertThat(inboxRepository.findAll()).allSatisfy(
            inbox -> assertThat(inbox.getStatus())
                .isEqualTo(com.menta.billing.infrastructure.webhook.WebhookInboxStatus.PROCESSED)
        );
    }

    @Test
    void webhook_handler_trips_capacity_invariant_flipping_purchase_to_exception() {
        UUID sessionId = seedSession(1);
        physicalCapacityAssignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), sessionId, UUID.randomUUID(), Instant.now()
        ));
        UUID paymentId = seedPendingPhysicalPayment("mp-capacity-full", UUID.randomUUID(), sessionId);
        when(paymentProviderPort.fetchPayment("mp-capacity-full")).thenReturn(
            new ProviderPaymentResult("approved", Money.of(new BigDecimal("100.00"), "ARS"), "ext-1", "merchant-1")
        );
        WebhookInboxJpaEntity row = receivedWebhookRow("mp-capacity-full", "req-1");

        worker.process(row);
        dispatchPendingOutboxEvent();

        assertThat(purchaseRepository.findByPaymentId(paymentId).orElseThrow().getStatus()).isEqualTo("EXCEPTION");
        assertThat(physicalCapacityAssignmentRepository.findAll()).hasSize(1);
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatusType()).isEqualTo("COMPLETED");
    }

    @Test
    void an_expired_provider_status_never_creates_billing_fulfillment() {
        seedPendingPhysicalPayment("mp-expired", UUID.randomUUID(), seedSession(2));
        when(paymentProviderPort.fetchPayment("mp-expired")).thenReturn(
            new ProviderPaymentResult("expired", Money.of(new BigDecimal("100.00"), "ARS"), "ext-1", "merchant-1")
        );
        WebhookInboxJpaEntity row = new WebhookInboxJpaEntity(
            "mp-expired:req-1", "mp-expired", "req-1",
            com.menta.billing.infrastructure.webhook.WebhookInboxStatus.RECEIVED, 0, null, null, Instant.now(), null
        );
        inboxRepository.save(row);

        worker.process(row);

        assertThat(paymentRepository.findAll().get(0).getStatusType()).isEqualTo("EXPIRED");
        assertThat(purchaseRepository.findAll()).isEmpty();
    }

    @Test
    void an_approved_status_with_mismatched_amount_goes_to_reconciliation_required_never_completed() {
        seedPendingPhysicalPayment("mp-mismatch", UUID.randomUUID(), seedSession(2));
        when(paymentProviderPort.fetchPayment("mp-mismatch")).thenReturn(
            new ProviderPaymentResult(
                "approved", Money.of(new BigDecimal("999.00"), "ARS"), "ext-1", "merchant-1"
            )
        );
        WebhookInboxJpaEntity row = new WebhookInboxJpaEntity(
            "mp-mismatch:req-1", "mp-mismatch", "req-1",
            com.menta.billing.infrastructure.webhook.WebhookInboxStatus.RECEIVED, 0, null, null, Instant.now(), null
        );
        inboxRepository.save(row);

        worker.process(row);

        assertThat(paymentRepository.findAll().get(0).getStatusType()).isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(purchaseRepository.findAll()).isEmpty();
        assertThat(reconciliationTaskRepository.findAll()).hasSize(1);
    }

    @Test
    void exhausting_provider_lookup_retries_creates_a_reconciliation_task() {
        UUID paymentId = seedPendingPhysicalPayment("mp-exhausted", UUID.randomUUID(), seedSession(2));
        when(paymentProviderPort.fetchPayment("mp-exhausted")).thenThrow(new RuntimeException("timeout"));
        WebhookInboxJpaEntity row = new WebhookInboxJpaEntity(
            "mp-exhausted:req-1", "mp-exhausted", "req-1",
            com.menta.billing.infrastructure.webhook.WebhookInboxStatus.RECEIVED, 1, null, null, Instant.now(), null
        );
        inboxRepository.save(row);

        worker.process(row);

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatusType())
            .isEqualTo("RECONCILIATION_REQUIRED");
        assertThat(reconciliationTaskRepository.findAll()).hasSize(1);
        assertThat(reconciliationTaskRepository.findAll().get(0).getPaymentId()).isEqualTo(paymentId);
    }

    @Test
    void a_provider_payment_id_with_no_local_payment_creates_a_task_referencing_only_the_event() {
        when(paymentProviderPort.fetchPayment(any())).thenReturn(
            new ProviderPaymentResult("approved", Money.of(new BigDecimal("1.00"), "ARS"), "x", "y")
        );
        WebhookInboxJpaEntity row = new WebhookInboxJpaEntity(
            "unknown:req-1", "unknown-provider-payment-id", "req-1",
            com.menta.billing.infrastructure.webhook.WebhookInboxStatus.RECEIVED, 0, null, null, Instant.now(), null
        );
        inboxRepository.save(row);

        worker.process(row);

        assertThat(reconciliationTaskRepository.findAll()).hasSize(1);
        assertThat(reconciliationTaskRepository.findAll().get(0).getPaymentId()).isNull();
    }
}
