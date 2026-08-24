package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.menta.app.outbox.OutboxReconciliationWorker;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.billing.application.dto.ProviderPaymentResult;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.domain.model.Money;
import com.menta.billing.infrastructure.persistence.entity.PaymentJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.WebhookInboxJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.WebhookInboxJpaRepository;
import com.menta.billing.infrastructure.webhook.WebhookInboxStatus;
import com.menta.billing.infrastructure.webhook.WebhookVerificationWorker;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves that a settled physical payment remains settled when its delivery
 * fails closed on capacity, while Billing records the EXCEPTION residual.
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
class PresentialPurchaseExceptionPathIntegrationTest {

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
        registry.add("billing.webhook.reconcile-rate-ms", () -> "999999999");
    }

    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private PurchaseJpaRepository purchaseRepository;
    @Autowired private WebhookInboxJpaRepository inboxRepository;
    @Autowired private OutboxRowJpaRepository outboxRepository;
    @Autowired private PhysicalCapacityAssignmentJpaRepository assignmentRepository;
    @Autowired private PhysicalCourseJpaRepository courseRepository;
    @Autowired private PhysicalSessionJpaRepository sessionRepository;
    @Autowired private WebhookVerificationWorker webhookWorker;
    @Autowired private OutboxReconciliationWorker outboxWorker;

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

    @SuppressWarnings("rawtypes")
    @MockBean
    private RedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        assignmentRepository.deleteAll();
        purchaseRepository.deleteAll();
        outboxRepository.deleteAll();
        inboxRepository.deleteAll();
        paymentRepository.deleteAll();
        sessionRepository.deleteAll();
        courseRepository.deleteAll();
    }

    @Test
    void capacity_trip_preserves_completed_payment_and_records_exception_purchase() {
        UUID sessionId = seedSessionWithCapacityOne();
        assignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), sessionId, UUID.randomUUID(), Instant.now()
        ));
        UUID paymentId = UUID.randomUUID();
        paymentRepository.save(new PaymentJpaEntity(
            paymentId, UUID.randomUUID(), "mp-exception-path", new BigDecimal("100.00"), "ARS",
            "ext-1", "merchant-1", "PHYSICAL", sessionId.toString(), "AWAITING_PROVIDER",
            null, null, Instant.now()
        ));
        when(paymentProviderPort.fetchPayment("mp-exception-path")).thenReturn(
            new ProviderPaymentResult(
                "approved", Money.of(new BigDecimal("100.00"), "ARS"), "ext-1", "merchant-1"
            )
        );
        WebhookInboxJpaEntity inbox = inboxRepository.save(new WebhookInboxJpaEntity(
            "mp-exception-path:req-1", "mp-exception-path", "req-1", WebhookInboxStatus.RECEIVED,
            0, null, null, Instant.now(), null
        ));

        webhookWorker.process(inbox);
        OutboxRowJpaEntity event = outboxRepository.findAll().getFirst();
        assertThat(outboxWorker.process(event)).isFalse();

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatusType())
            .isEqualTo("COMPLETED");
        assertThat(purchaseRepository.findByPaymentId(paymentId).orElseThrow().getStatus())
            .isEqualTo("EXCEPTION");
        assertThat(assignmentRepository.countBySessionId(sessionId)).isEqualTo(1);
    }

    private UUID seedSessionWithCapacityOne() {
        UUID courseId = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new PhysicalCourseJpaEntity(
            courseId, "Integration course", "Test course", UUID.randomUUID(), "Test professor",
            "MONDAY", LocalTime.NOON, 60, "BEGINNER", 1, CourseStatus.ACTIVE, now, now
        ));
        UUID sessionId = UUID.randomUUID();
        sessionRepository.save(new PhysicalSessionJpaEntity(
            sessionId, courseId, now, 1, "SCHEDULED", null
        ));
        return sessionId;
    }
}
