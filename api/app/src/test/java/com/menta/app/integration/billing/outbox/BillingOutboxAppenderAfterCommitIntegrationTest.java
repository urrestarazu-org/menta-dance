package com.menta.app.integration.billing.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.application.dto.ProviderPaymentResult;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.usecase.PaymentVerificationService;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.domain.model.Money;
import com.menta.billing.infrastructure.outbox.BillingOutboxRowJpaRepository;
import com.menta.billing.infrastructure.persistence.entity.PaymentJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.WebhookInboxJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.ReconciliationTaskJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.WebhookInboxJpaRepository;
import com.menta.billing.infrastructure.webhook.WebhookInboxStatus;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * MySQL-backed transactional-outbox coverage for a completed physical payment.
 *
 * <p>The producer appends through {@code BillingOutboxAppender} while the
 * payment transaction is still active. Therefore committing makes exactly one
 * event visible and rolling back makes neither the payment update nor the
 * event visible. The app-level capacity handler is intentionally not driven
 * here; this test fences only the producer transaction boundary.</p>
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
class BillingOutboxAppenderAfterCommitIntegrationTest {

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
        registry.add("billing.webhook.mercadopago.hmac-secret", () -> "integration-test-webhook-secret");
        registry.add("billing.webhook.reconcile-rate-ms", () -> "999999999");
        registry.add("billing.webhook.max-attempts", () -> "2");
    }

    @Autowired private PaymentVerificationService paymentVerificationService;
    @Autowired private WebhookInboxJpaRepository inboxRepository;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private PurchaseJpaRepository purchaseRepository;
    @Autowired private BillingOutboxRowJpaRepository outboxRepository;
    @Autowired private ReconciliationTaskJpaRepository reconciliationTaskRepository;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private javax.sql.DataSource dataSource;

    @MockBean private PaymentProviderPort paymentProviderPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;
    @MockBean private ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;
    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;

    @AfterEach
    void cleanUp() {
        reconciliationTaskRepository.deleteAllInBatch();
        outboxRepository.deleteAllInBatch();
        purchaseRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        inboxRepository.deleteAllInBatch();
    }

    private void seedPendingPhysicalPayment(String providerPaymentId, String externalReference) {
        paymentRepository.save(new PaymentJpaEntity(
            UUID.randomUUID(), UUID.randomUUID(), providerPaymentId, new BigDecimal("100.00"),
            "ARS", externalReference, "merchant-1", "PHYSICAL", UUID.randomUUID().toString(),
            "AWAITING_PROVIDER", null, null, Instant.now()
        ));
    }

    private void wireApproved(String providerPaymentId, String externalReference) {
        when(paymentProviderPort.fetchPayment(providerPaymentId)).thenReturn(
            new ProviderPaymentResult(
                "approved", Money.of(new BigDecimal("100.00"), "ARS"),
                externalReference, "merchant-1"
            )
        );
    }

    private long jdbcOutboxCount() {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("SELECT count(*) FROM common_outbox_events");
             var rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new IllegalStateException("MySQL probe failed", e);
        }
    }

    @Test
    void committed_physical_payment_persists_one_outbox_event_in_the_same_transaction() {
        String providerId = "mp-commit-test";
        String externalRef = "ext-commit-test";
        UUID paymentId = UUID.randomUUID();
        paymentRepository.save(new PaymentJpaEntity(
            paymentId, UUID.randomUUID(), providerId, new BigDecimal("100.00"),
            "ARS", externalRef, "merchant-1", "PHYSICAL", UUID.randomUUID().toString(),
            "AWAITING_PROVIDER", null, null, Instant.now()
        ));
        wireApproved(providerId, externalRef);

        new TransactionTemplate(txManager).executeWithoutResult(status -> paymentVerificationService.verify(providerId));

        assertThat(outboxRepository.findAll())
            .singleElement()
            .satisfies(row -> {
                assertThat(row.getEventType()).isEqualTo("billing.PhysicalPaymentCompleted");
                assertThat(row.getAggregateId()).isEqualTo(paymentId.toString());
            });
        assertThat(purchaseRepository.findAll()).isEmpty();
    }

    @Test
    void payment_rollback_leaves_zero_outbox_and_zero_purchase_rows() {
        String providerId = "mp-rollback-test";
        String externalRef = "ext-rollback-test";
        seedPendingPhysicalPayment(providerId, externalRef);
        wireApproved(providerId, externalRef);

        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            paymentVerificationService.verify(providerId);
            status.setRollbackOnly();
        });

        assertThat(outboxRepository.findAll())
            .as("Spec: Rolled-back payment leaves empty outbox and empty purchases")
            .isEmpty();
        assertThat(purchaseRepository.findAll())
            .as("Producer does not insert any Purchase row on rollback")
            .isEmpty();
        assertThat(jdbcOutboxCount())
            .as("No outbox row survives the payment transaction rollback")
            .isZero();
    }

}
