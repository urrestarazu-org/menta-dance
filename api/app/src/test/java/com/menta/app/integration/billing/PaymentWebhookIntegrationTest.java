package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.application.dto.ProviderPaymentResult;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.application.port.out.PhysicalCapacityAssignmentPort;
import com.menta.billing.application.port.out.VirtualAccessGrantPort;
import com.menta.billing.domain.model.Money;
import com.menta.billing.infrastructure.persistence.entity.PaymentJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.WebhookInboxJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.ReconciliationTaskJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.WebhookInboxJpaRepository;
import com.menta.billing.infrastructure.webhook.WebhookVerificationWorker;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
    @Autowired private PurchaseJpaRepository purchaseRepository;
    @Autowired private ReconciliationTaskJpaRepository reconciliationTaskRepository;
    @Autowired private WebhookVerificationWorker worker;

    @MockBean private PaymentProviderPort paymentProviderPort;
    @MockBean private PhysicalCapacityAssignmentPort physicalCapacityAssignmentPort;
    @MockBean private VirtualAccessGrantPort virtualAccessGrantPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;
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
        paymentRepository.deleteAll();
        inboxRepository.deleteAll();
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

    private UUID seedPendingPhysicalPayment(String providerPaymentId) {
        UUID id = UUID.randomUUID();
        paymentRepository.save(new PaymentJpaEntity(
            id, providerPaymentId, new BigDecimal("100.00"), "ARS", "ext-1", "merchant-1",
            "PHYSICAL", "session-1", "AWAITING_PROVIDER", null, null, Instant.now()
        ));
        return id;
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
    void the_worker_confirms_a_matching_payment_and_never_double_assigns_capacity_on_retry() {
        UUID paymentId = seedPendingPhysicalPayment("mp-idempotent");
        when(paymentProviderPort.fetchPayment("mp-idempotent")).thenReturn(
            new ProviderPaymentResult("approved", Money.of(new BigDecimal("100.00"), "ARS"), "ext-1", "merchant-1")
        );
        WebhookInboxJpaEntity row = new WebhookInboxJpaEntity(
            "mp-idempotent:req-1", "mp-idempotent", "req-1",
            com.menta.billing.infrastructure.webhook.WebhookInboxStatus.RECEIVED, 0, null, null, Instant.now(), null
        );
        inboxRepository.save(row);

        worker.process(row);
        worker.process(row);

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatusType()).isEqualTo("COMPLETED");
        assertThat(purchaseRepository.findAll()).hasSize(1);
        org.mockito.Mockito.verify(physicalCapacityAssignmentPort, org.mockito.Mockito.times(1))
            .assign(org.mockito.ArgumentMatchers.eq("session-1"), any());
    }

    @Test
    void a_failed_capacity_assignment_still_completes_the_payment_but_marks_the_purchase_exception() {
        seedPendingPhysicalPayment("mp-fulfillment-fails");
        when(paymentProviderPort.fetchPayment("mp-fulfillment-fails")).thenReturn(
            new ProviderPaymentResult("approved", Money.of(new BigDecimal("100.00"), "ARS"), "ext-1", "merchant-1")
        );
        doThrow(new RuntimeException("capacity full"))
            .when(physicalCapacityAssignmentPort).assign(any(), any());
        WebhookInboxJpaEntity row = new WebhookInboxJpaEntity(
            "mp-fulfillment-fails:req-1", "mp-fulfillment-fails", "req-1",
            com.menta.billing.infrastructure.webhook.WebhookInboxStatus.RECEIVED, 0, null, null, Instant.now(), null
        );
        inboxRepository.save(row);

        worker.process(row);

        assertThat(purchaseRepository.findAll()).hasSize(1);
        assertThat(purchaseRepository.findAll().get(0).getStatus()).isEqualTo("EXCEPTION");
    }

    @Test
    void an_expired_provider_status_never_creates_a_purchase() {
        seedPendingPhysicalPayment("mp-expired");
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
        seedPendingPhysicalPayment("mp-mismatch");
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
        UUID paymentId = seedPendingPhysicalPayment("mp-exhausted");
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
