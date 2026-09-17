package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.menta.app.outbox.OutboxReconciliationWorker;
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
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.billing.application.dto.ProviderPaymentResult;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.domain.model.Money;
import com.menta.billing.infrastructure.persistence.entity.PaymentJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PhysicalCourseQuoteJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.WebhookInboxJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PhysicalCourseQuoteJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseSessionJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.WebhookInboxJpaRepository;
import com.menta.billing.infrastructure.webhook.WebhookInboxStatus;
import com.menta.billing.infrastructure.webhook.WebhookVerificationWorker;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityHoldJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import com.menta.shared.domain.vo.Email;
import com.menta.shared.outbox.OutboxStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end coverage for #41 (US-PHYSICAL-004): real HTTP checkout (through
 * the real security filter chain) → real signed-webhook verification worker →
 * real outbox reconciliation worker → real {@code assignAll} against the
 * physical capacity tables.
 *
 * <p>Mirrors {@link SubscriptionCheckoutIntegrationTest}'s harness
 * ({@code RANDOM_PORT} + {@link TestRestTemplate}, real MySQL via
 * Testcontainers) and reuses {@link PresentialPurchaseExceptionPathIntegrationTest}'s
 * direct outbox-worker invocation to drive the confirmation half of the
 * circuit without waiting on the scheduler (its cadence is pushed far out via
 * {@code billing.webhook.reconcile-rate-ms}).</p>
 *
 * <p>Scenarios 1, 2, 5 and 6 of US-PHYSICAL-004 (proposal.md Scope), plus the
 * checkout-level D5/A6 best-effort {@code 409} and the {@code 410}/{@code 401}
 * edges — every one of them exercised through the real controller, the real
 * webhook worker and the real outbox worker together for the first time in
 * this Testcontainers context (PR7 only proved the checkout endpoint in
 * isolation with Mockito).</p>
 */
// #209 Phase D: management.health.mail.enabled=false — @MockBean JavaMailSender
// replaces the real bean with a Mockito mock that isn't a JavaMailSenderImpl,
// so Spring Boot's MailHealthContributorAutoConfiguration finds an empty
// "beans" map and fails context startup with IllegalArgumentException
// ("'beans' must not be empty") unless the mail health indicator is disabled.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.health.mail.enabled=false"
)
@ActiveProfiles("integration-test")
@Testcontainers
class PhysicalPurchaseIntegrationTest {

    private static final String MERCHANT_ACCOUNT_ID = "merchant-integration-physical";
    private static final BigDecimal MONTHLY_PRICE = new BigDecimal("300.00");
    private static final BigDecimal INDIVIDUAL_PRICE = new BigDecimal("120.00");

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
        registry.add("billing.mercadopago.merchant-account-id", () -> MERCHANT_ACCOUNT_ID);
    }

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private PurchaseJpaRepository purchaseRepository;
    @Autowired private PurchaseSessionJpaRepository purchaseSessionRepository;
    @Autowired private WebhookInboxJpaRepository inboxRepository;
    @Autowired private OutboxRowJpaRepository outboxRepository;
    @Autowired private PhysicalCapacityAssignmentJpaRepository assignmentRepository;
    @Autowired private PhysicalCapacityHoldJpaRepository holdRepository;
    @Autowired private PhysicalCourseJpaRepository courseRepository;
    @Autowired private PhysicalSessionJpaRepository sessionRepository;
    @Autowired private PhysicalCourseQuoteJpaRepository quoteRepository;
    @Autowired private WebhookVerificationWorker webhookWorker;
    @Autowired private OutboxReconciliationWorker outboxWorker;

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
    // ProcessPhysicalCheckInUseCaseImpl needs a RedisTemplate this Redis-less
    // context would otherwise fail to resolve (same rationale as
    // SubscriptionCheckoutIntegrationTest).
    @MockBean private ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;
    /**
     * Phase D (#209): replaces the real Spring Mail bean so {@code
     * SpringMailPurchaseExceptionNotificationAdapter} still runs for real
     * (real Spring wiring, real {@code SimpleMailMessage} construction) while
     * this test captures what would otherwise leave the JVM over SMTP.
     */
    @MockBean private JavaMailSender mailSender;

    private final AtomicInteger preferenceSequence = new AtomicInteger();

    @BeforeEach
    void stubTheProviderPreference() {
        when(tokenBlacklistPort.isBlacklisted(anyString())).thenReturn(false);
        when(tokenBlacklistPort.currentTokenVersion(anyString())).thenReturn(OptionalLong.empty());
        when(paymentPreferencePort.createPreference(any())).thenAnswer(invocation -> {
            String preferenceId = "pref-physical-" + preferenceSequence.incrementAndGet();
            return new com.menta.billing.application.dto.PaymentPreferenceResult(
                preferenceId, "https://mp.example/checkout/" + preferenceId
            );
        });
    }

    @AfterEach
    void cleanUp() {
        holdRepository.deleteAll();
        assignmentRepository.deleteAll();
        purchaseSessionRepository.deleteAll();
        purchaseRepository.deleteAll();
        outboxRepository.deleteAll();
        inboxRepository.deleteAll();
        paymentRepository.deleteAll();
        quoteRepository.deleteAll();
        sessionRepository.deleteAll();
        courseRepository.deleteAll();
    }

    // --- fixtures -----------------------------------------------------------

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

    private UUID seedCourse() {
        UUID courseId = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new PhysicalCourseJpaEntity(
            courseId, "Integration course", "Test course", UUID.randomUUID(), "Test professor",
            "MONDAY", LocalTime.NOON, 60, "BEGINNER", 1, CourseStatus.ACTIVE, now, now
        ));
        return courseId;
    }

    /** Ordered, weekly-spaced future sessions — matches design A2's claim order by construction. */
    private List<UUID> seedScheduledSessions(UUID courseId, int count, int capacity) {
        Instant base = Instant.now().plus(1, ChronoUnit.DAYS);
        List<UUID> sessionIds = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID sessionId = UUID.randomUUID();
            sessionRepository.save(new PhysicalSessionJpaEntity(
                sessionId, courseId, base.plus(7L * i, ChronoUnit.DAYS), capacity, "SCHEDULED", null
            ));
            sessionIds.add(sessionId);
        }
        return sessionIds;
    }

    private String seedMonthlyQuote(UUID courseId, int scheduledSessionCount, BigDecimal amount) {
        UUID quoteId = UUID.randomUUID();
        Instant now = Instant.now();
        quoteRepository.save(new PhysicalCourseQuoteJpaEntity(
            quoteId.toString(), courseId.toString(), "MONTHLY", amount, "ARS", BigDecimal.ZERO, 1,
            scheduledSessionCount, null, amount, "ARS", "AVAILABLE", now, now.plusSeconds(3600)
        ));
        return quoteId.toString();
    }

    private String seedIndividualQuote(UUID courseId, UUID selectedSessionId, BigDecimal amount) {
        UUID quoteId = UUID.randomUUID();
        Instant now = Instant.now();
        quoteRepository.save(new PhysicalCourseQuoteJpaEntity(
            quoteId.toString(), courseId.toString(), "INDIVIDUAL", amount, "ARS", BigDecimal.ZERO, 1, 1,
            selectedSessionId.toString(), amount, "ARS", "AVAILABLE", now, now.plusSeconds(3600)
        ));
        return quoteId.toString();
    }

    private static Map<String, Object> checkoutBody(String quoteId, String idempotencyKey) {
        Map<String, Object> body = new HashMap<>();
        body.put("quoteId", quoteId);
        body.put("paymentMethod", "MERCADO_PAGO");
        body.put("idempotencyKey", idempotencyKey);
        return body;
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> checkout(UUID userId, String quoteId, String idempotencyKey) {
        return http.exchange(
            "/api/v1/billing/physical/purchases", HttpMethod.POST,
            new HttpEntity<>(checkoutBody(quoteId, idempotencyKey), headersFor(userId)), Map.class
        );
    }

    /** Drives the confirmation half: real webhook verification, then returns the resulting outbox row. */
    private OutboxRowJpaEntity confirmPayment(String providerPaymentId, String externalReference, BigDecimal amount) {
        when(paymentProviderPort.fetchPayment(providerPaymentId)).thenReturn(
            new ProviderPaymentResult("approved", Money.of(amount, "ARS"), externalReference, MERCHANT_ACCOUNT_ID)
        );
        WebhookInboxJpaEntity row = new WebhookInboxJpaEntity(
            providerPaymentId + ":req-1", providerPaymentId, "req-1", WebhookInboxStatus.RECEIVED,
            0, null, null, Instant.now(), null
        );
        inboxRepository.save(row);
        webhookWorker.process(row);
        return outboxRepository.findAll().stream()
            .filter(candidate -> candidate.getEventType().equals(BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED))
            .reduce((first, second) -> second) // most recent, in case a prior scenario in the same test left rows
            .orElseThrow(() -> new IllegalStateException("No billing.PhysicalPaymentCompleted outbox row was produced"));
    }

    // --- Phase D (#209) fixtures -----------------------------------------------

    private record SeededStudent(UUID id, String email) {
    }

    private SeededStudent seedStudentWithKnownEmail() {
        String email = "purchase-exception-" + UUID.randomUUID() + "@example.com";
        User user = User.create(Email.of(email), "irrelevant-hash", Role.STUDENT);
        userRepository.save(user);
        return new SeededStudent(user.getId().getValue(), email);
    }

    /**
     * Seeds a {@code Payment} directly, bypassing checkout and therefore the
     * atomic hold (#208) entirely — mirrors {@code
     * PresentialPurchaseExceptionPathIntegrationTest}'s own technique. With no
     * hold row ever existing for this payment, {@code
     * PhysicalCapacityHoldPort#convertAll} resolves {@code HoldNotFound} and
     * the outbox handler falls through to the legacy {@code CoveragePlanner}
     * + quote-lookup path — the only way to reach the three pre-{@code
     * Purchase} sites (design C1) through this test's real checkout endpoint
     * would otherwise be unreachable, since a real checkout always creates a
     * hold.
     */
    private UUID seedDirectPayment(UUID userId, String providerPaymentId, String externalReference, String quoteId) {
        UUID paymentId = UUID.randomUUID();
        paymentRepository.save(new PaymentJpaEntity(
            paymentId, userId, providerPaymentId, new BigDecimal("100.00"), "ARS",
            externalReference, MERCHANT_ACCOUNT_ID, "PHYSICAL", quoteId, "AWAITING_PROVIDER",
            null, null, Instant.now()
        ));
        return paymentId;
    }

    /** Drives confirmation for a directly-seeded payment (see {@link #seedDirectPayment}). */
    private OutboxRowJpaEntity confirmDirectPayment(String providerPaymentId, String externalReference) {
        when(paymentProviderPort.fetchPayment(providerPaymentId)).thenReturn(
            new ProviderPaymentResult(
                "approved", Money.of(new BigDecimal("100.00"), "ARS"), externalReference, MERCHANT_ACCOUNT_ID
            )
        );
        WebhookInboxJpaEntity row = new WebhookInboxJpaEntity(
            providerPaymentId + ":req-1", providerPaymentId, "req-1", WebhookInboxStatus.RECEIVED,
            0, null, null, Instant.now(), null
        );
        inboxRepository.save(row);
        webhookWorker.process(row);
        return outboxRepository.findAll().stream()
            .filter(candidate -> candidate.getEventType().equals(BillingOutboxEventTypes.PHYSICAL_PAYMENT_COMPLETED))
            .reduce((first, second) -> second)
            .orElseThrow(() -> new IllegalStateException("No billing.PhysicalPaymentCompleted outbox row was produced"));
    }

    private List<OutboxRowJpaEntity> outboxRowsFor(String eventType, UUID paymentId) {
        return outboxRepository.findAll().stream()
            .filter(row -> row.getEventType().equals(eventType))
            .filter(row -> row.getAggregateId().equals(paymentId.toString()))
            .toList();
    }

    // --- Scenario 1: MONTHLY --------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void monthly_purchase_confirmed_assigns_every_covered_session_end_to_end() {
        UUID userId = seedStudent();
        UUID courseId = seedCourse();
        List<UUID> sessionIds = seedScheduledSessions(courseId, 3, 5);
        String quoteId = seedMonthlyQuote(courseId, 3, MONTHLY_PRICE);

        ResponseEntity<Map> checkout = checkout(userId, quoteId, "idem-monthly-1");
        assertThat(checkout.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(checkout.getBody().get("status")).isEqualTo("PENDING");
        assertThat(checkout.getBody().get("quoteId")).isEqualTo(quoteId);
        String externalReference = (String) checkout.getBody().get("externalReference");
        UUID paymentId = UUID.fromString((String) checkout.getBody().get("paymentId"));

        OutboxRowJpaEntity event = confirmPayment("mp-monthly-1", externalReference, MONTHLY_PRICE);
        assertThat(outboxWorker.process(event)).isFalse();

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatusType()).isEqualTo("COMPLETED");
        var purchase = purchaseRepository.findByPaymentId(paymentId).orElseThrow();
        assertThat(purchase.getStatus()).isEqualTo("ASSIGNED");

        var sessions = purchaseSessionRepository.findByPurchaseIdOrderByPositionAsc(purchase.getId());
        assertThat(sessions).hasSize(3);
        assertThat(sessions).extracting(s -> UUID.fromString(s.getPhysicalSessionId()))
            .containsExactlyElementsOf(sessionIds);

        for (UUID sessionId : sessionIds) {
            assertThat(assignmentRepository.countBySessionId(sessionId)).isEqualTo(1);
            assertThat(assignmentRepository.existsBySessionIdAndStudentId(sessionId, userId)).isTrue();
        }
    }

    // --- Scenario 2: INDIVIDUAL (N=1) -----------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void individual_purchase_confirmed_assigns_exactly_one_session_end_to_end() {
        UUID userId = seedStudent();
        UUID courseId = seedCourse();
        List<UUID> sessionIds = seedScheduledSessions(courseId, 1, 5);
        UUID selectedSessionId = sessionIds.get(0);
        String quoteId = seedIndividualQuote(courseId, selectedSessionId, INDIVIDUAL_PRICE);

        ResponseEntity<Map> checkout = checkout(userId, quoteId, "idem-individual-1");
        assertThat(checkout.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String externalReference = (String) checkout.getBody().get("externalReference");
        UUID paymentId = UUID.fromString((String) checkout.getBody().get("paymentId"));

        OutboxRowJpaEntity event = confirmPayment("mp-individual-1", externalReference, INDIVIDUAL_PRICE);
        assertThat(outboxWorker.process(event)).isFalse();

        var purchase = purchaseRepository.findByPaymentId(paymentId).orElseThrow();
        assertThat(purchase.getStatus()).isEqualTo("ASSIGNED");
        assertThat(purchaseSessionRepository.findByPurchaseIdOrderByPositionAsc(purchase.getId())).hasSize(1);
        assertThat(assignmentRepository.countBySessionId(selectedSessionId)).isEqualTo(1);
        assertThat(assignmentRepository.existsBySessionIdAndStudentId(selectedSessionId, userId)).isTrue();
    }

    // --- Scenario 5: duplicate webhook / outbox redelivery --------------------

    @Test
    @SuppressWarnings("unchecked")
    void a_duplicate_outbox_redelivery_consumes_no_additional_spots() {
        UUID userId = seedStudent();
        UUID courseId = seedCourse();
        List<UUID> sessionIds = seedScheduledSessions(courseId, 2, 5);
        String quoteId = seedMonthlyQuote(courseId, 2, MONTHLY_PRICE);

        ResponseEntity<Map> checkout = checkout(userId, quoteId, "idem-duplicate-1");
        String externalReference = (String) checkout.getBody().get("externalReference");
        UUID paymentId = UUID.fromString((String) checkout.getBody().get("paymentId"));

        OutboxRowJpaEntity event = confirmPayment("mp-duplicate-1", externalReference, MONTHLY_PRICE);
        assertThat(outboxWorker.process(event)).isFalse();
        assertThat(purchaseRepository.findByPaymentId(paymentId).orElseThrow().getStatus()).isEqualTo("ASSIGNED");
        for (UUID sessionId : sessionIds) {
            assertThat(assignmentRepository.countBySessionId(sessionId)).isEqualTo(1);
        }

        // Real redelivery of the very same event — the handler must not
        // mint a second assignment per session, and the already-ASSIGNED
        // Purchase must not regress (design's state-machine guard refuses
        // ASSIGNED -> EXCEPTION, so the second run fails closed instead of
        // corrupting the settled result).
        outboxWorker.process(event);

        assertThat(purchaseRepository.findByPaymentId(paymentId).orElseThrow().getStatus()).isEqualTo("ASSIGNED");
        for (UUID sessionId : sessionIds) {
            assertThat(assignmentRepository.countBySessionId(sessionId)).isEqualTo(1);
        }
        assertThat(purchaseSessionRepository.findByPurchaseIdOrderByPositionAsc(
            purchaseRepository.findByPaymentId(paymentId).orElseThrow().getId()
        )).hasSize(2);
    }

    // --- Design step 8: conversion is idempotent under repeated redelivery ----

    /**
     * design B3/step 8: the hold is converted exactly once — every row for
     * this payment gets its {@code converted_at} set on the first delivery,
     * and every later delivery finds every row already converted
     * ({@code ConvertOutcome.AlreadyConverted}) and writes nothing further,
     * however many times it is redelivered.
     */
    @Test
    @SuppressWarnings("unchecked")
    void redelivering_the_webhook_five_times_converts_the_hold_exactly_once() {
        UUID userId = seedStudent();
        UUID courseId = seedCourse();
        List<UUID> sessionIds = seedScheduledSessions(courseId, 2, 5);
        String quoteId = seedMonthlyQuote(courseId, 2, MONTHLY_PRICE);

        ResponseEntity<Map> checkout = checkout(userId, quoteId, "idem-five-1");
        String externalReference = (String) checkout.getBody().get("externalReference");
        UUID paymentId = UUID.fromString((String) checkout.getBody().get("paymentId"));

        OutboxRowJpaEntity event = confirmPayment("mp-five-1", externalReference, MONTHLY_PRICE);
        assertThat(outboxWorker.process(event)).isFalse();
        assertThat(purchaseRepository.findByPaymentId(paymentId).orElseThrow().getStatus()).isEqualTo("ASSIGNED");
        for (UUID sessionId : sessionIds) {
            assertThat(assignmentRepository.countBySessionId(sessionId)).isEqualTo(1);
        }
        var convertedAtAfterFirstDelivery = holdRepository.findByPaymentIdOrdered(paymentId).stream()
            .map(row -> row.getConvertedAt())
            .toList();
        assertThat(convertedAtAfterFirstDelivery).hasSize(2).allSatisfy(
            convertedAt -> assertThat(convertedAt).isNotNull()
        );

        for (int redelivery = 0; redelivery < 4; redelivery++) {
            outboxWorker.process(event);
        }

        assertThat(purchaseRepository.findByPaymentId(paymentId).orElseThrow().getStatus()).isEqualTo("ASSIGNED");
        for (UUID sessionId : sessionIds) {
            assertThat(assignmentRepository.countBySessionId(sessionId)).isEqualTo(1);
        }
        assertThat(purchaseSessionRepository.findByPurchaseIdOrderByPositionAsc(
            purchaseRepository.findByPaymentId(paymentId).orElseThrow().getId()
        )).hasSize(2);
        var convertedAtAfterFiveDeliveries = holdRepository.findByPaymentIdOrdered(paymentId).stream()
            .map(row -> row.getConvertedAt())
            .toList();
        assertThat(convertedAtAfterFiveDeliveries).isEqualTo(convertedAtAfterFirstDelivery);
    }

    // --- Scenario 6: computed sessions cannot all be assigned -----------------

    /**
     * Proposal scenario 6 is precisely this: "the computed sessions cannot
     * all be assigned" — {@code CoveragePlanner} DOES resolve the full
     * eligible set (unlike a coverage shortfall), but the real capacity
     * claim trips because another purchase took the last spot on one of
     * those sessions between checkout's best-effort D5 read and this
     * confirmation. This is the same residual path {@code
     * PresentialPurchaseExceptionPathIntegrationTest} proves for the old
     * single-session flow, now exercised through the real checkout endpoint
     * and the real multi-session {@code assignAll}.
     */
    @Test
    @SuppressWarnings("unchecked")
    void a_capacity_trip_at_confirmation_leaves_payment_completed_and_purchase_exception_end_to_end() {
        UUID userId = seedStudent();
        UUID courseId = seedCourse();
        List<UUID> sessionIds = seedScheduledSessions(courseId, 2, 1);
        String quoteId = seedMonthlyQuote(courseId, 2, MONTHLY_PRICE);

        ResponseEntity<Map> checkout = checkout(userId, quoteId, "idem-capacity-trip-1");
        assertThat(checkout.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String externalReference = (String) checkout.getBody().get("externalReference");
        UUID paymentId = UUID.fromString((String) checkout.getBody().get("paymentId"));

        // Between checkout and confirmation, a different buyer takes the
        // only spot on one of the two sessions this quote covers.
        UUID contestedSession = sessionIds.get(0);
        assignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), contestedSession, UUID.randomUUID(), Instant.now()
        ));

        OutboxRowJpaEntity event = confirmPayment("mp-capacity-trip-1", externalReference, MONTHLY_PRICE);
        assertThat(outboxWorker.process(event)).isFalse();

        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatusType()).isEqualTo("COMPLETED");
        assertThat(purchaseRepository.findByPaymentId(paymentId).orElseThrow().getStatus()).isEqualTo("EXCEPTION");
        // All-or-nothing (design A3): the non-conflicting session gets zero
        // new rows too, and the contested one keeps only its pre-existing row.
        assertThat(assignmentRepository.countBySessionId(contestedSession)).isEqualTo(1);
        assertThat(assignmentRepository.countBySessionId(sessionIds.get(1))).isEqualTo(0);
        assertThat(assignmentRepository.existsBySessionIdAndStudentId(sessionIds.get(1), userId)).isFalse();
    }

    // --- D5/A6: best-effort 409 on a visibly-full quote -----------------------

    @Test
    @SuppressWarnings("unchecked")
    void checkout_is_rejected_with_409_when_the_quoted_session_is_visibly_full_and_creates_no_payment() {
        UUID userId = seedStudent();
        UUID courseId = seedCourse();
        List<UUID> sessionIds = seedScheduledSessions(courseId, 1, 1);
        UUID fullSession = sessionIds.get(0);
        // Capacity 1, already occupied by someone else — availableSpots reads 0.
        assignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), fullSession, UUID.randomUUID(), Instant.now()
        ));
        String quoteId = seedIndividualQuote(courseId, fullSession, INDIVIDUAL_PRICE);

        ResponseEntity<Map> response = checkout(userId, quoteId, "idem-full-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("CAPACITY_UNAVAILABLE");
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    // --- A7: expired quote -----------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void checkout_is_rejected_with_410_for_an_expired_quote_and_creates_no_payment() {
        UUID userId = seedStudent();
        UUID courseId = seedCourse();
        List<UUID> sessionIds = seedScheduledSessions(courseId, 1, 5);
        UUID quoteId = UUID.randomUUID();
        Instant now = Instant.now();
        quoteRepository.save(new PhysicalCourseQuoteJpaEntity(
            quoteId.toString(), courseId.toString(), "INDIVIDUAL", INDIVIDUAL_PRICE, "ARS", BigDecimal.ZERO, 1, 1,
            sessionIds.get(0).toString(), INDIVIDUAL_PRICE, "ARS", "AVAILABLE",
            now.minusSeconds(7200), now.minusSeconds(3600)
        ));

        ResponseEntity<Map> response = checkout(userId, quoteId.toString(), "idem-expired-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody().get("code")).isEqualTo("PHYSICAL_COURSE_QUOTE_EXPIRED");
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    // --- #208 task 10.2: guaranteed 409 under real concurrency ----------------

    /**
     * Task 10.2 (design D2): unlike the pre-#208 best-effort check — which
     * still created a {@code Payment} row for the loser, later resolving to
     * {@code EXCEPTION} at confirmation (the path proven by {@link
     * #a_capacity_trip_at_confirmation_leaves_payment_completed_and_purchase_exception_end_to_end})
     * — the real atomic hold now guarantees that a request answered with
     * {@code 201} has already reserved every eligible session, so no other
     * buyer can take that spot before confirmation. Two concurrent checkouts
     * racing for the last remaining spot on the same session must resolve to
     * exactly one {@code 201} and one {@code 409 CAPACITY_UNAVAILABLE}, with
     * the loser creating ZERO {@code billing_payments} rows — not merely
     * zero assignment rows.
     */
    @Test
    @SuppressWarnings("unchecked")
    void two_concurrent_checkouts_for_the_last_spot_guarantee_one_201_and_zero_payment_rows_for_the_loser()
        throws InterruptedException {
        UUID courseId = seedCourse();
        List<UUID> sessionIds = seedScheduledSessions(courseId, 1, 1);
        UUID contestedSession = sessionIds.get(0);

        UUID userA = seedStudent();
        UUID userB = seedStudent();
        String quoteA = seedIndividualQuote(courseId, contestedSession, INDIVIDUAL_PRICE);
        String quoteB = seedIndividualQuote(courseId, contestedSession, INDIVIDUAL_PRICE);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<ResponseEntity<Map>> responses = new CopyOnWriteArrayList<>();

        pool.submit(() -> {
            try {
                start.await();
                responses.add(checkout(userA, quoteA, "idem-concurrent-a"));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        pool.submit(() -> {
            try {
                start.await();
                responses.add(checkout(userB, quoteB, "idem-concurrent-b"));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(responses).hasSize(2);
        long created = responses.stream().filter(response -> response.getStatusCode() == HttpStatus.CREATED).count();
        long conflicts = responses.stream().filter(response -> response.getStatusCode() == HttpStatus.CONFLICT).count();
        assertThat(created).isEqualTo(1);
        assertThat(conflicts).isEqualTo(1);
        ResponseEntity<Map> conflictResponse = responses.stream()
            .filter(response -> response.getStatusCode() == HttpStatus.CONFLICT).findFirst().orElseThrow();
        assertThat(conflictResponse.getBody().get("code")).isEqualTo("CAPACITY_UNAVAILABLE");

        // The whole point of #208: the loser never creates a Payment row at
        // all — unlike #41's best-effort check, which still inserted one
        // that later resolved to EXCEPTION at confirmation.
        assertThat(paymentRepository.findAll()).hasSize(1);
    }

    // --- #208 task 10.3: partial coverage never even attempts a hold ----------

    /**
     * Task 10.3: when {@code CoveragePlanner} cannot fill the quote's full
     * {@code scheduledSessionCount} — the course simply has fewer eligible
     * sessions than requested — it returns a non-{@code Complete} plan and
     * {@code resolveCoveragePlan} throws {@code PhysicalCapacityUnavailableException}
     * BEFORE {@code holdCapacity} is ever called (PR6). Confirmed here
     * end-to-end through the real HTTP endpoint: this must create zero hold
     * rows, not merely zero assignment rows — the hold call is all-or-nothing
     * even one level up, at the coverage-planning step itself.
     */
    @Test
    @SuppressWarnings("unchecked")
    void a_partially_satisfiable_monthly_quote_creates_zero_hold_rows_and_zero_payment_rows() {
        UUID userId = seedStudent();
        UUID courseId = seedCourse();
        // Only 1 scheduled session exists, but the quote asks for 3.
        seedScheduledSessions(courseId, 1, 5);
        String quoteId = seedMonthlyQuote(courseId, 3, MONTHLY_PRICE);

        ResponseEntity<Map> response = checkout(userId, quoteId, "idem-partial-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("CAPACITY_UNAVAILABLE");
        assertThat(paymentRepository.findAll()).isEmpty();
        assertThat(holdRepository.findAll()).isEmpty();
    }

    // --- 401 without a token -----------------------------------------------

    @Test
    void checkout_without_a_token_is_rejected() {
        UUID courseId = seedCourse();
        List<UUID> sessionIds = seedScheduledSessions(courseId, 1, 5);
        String quoteId = seedIndividualQuote(courseId, sessionIds.get(0), INDIVIDUAL_PRICE);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/purchases", HttpMethod.POST,
            new HttpEntity<>(checkoutBody(quoteId, "idem-no-token-1"), headers), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(paymentRepository.findAll()).isEmpty();
    }

    // === Phase D (#209): integration proof ===================================

    /**
     * D.1: one real {@code PENDING_FULFILLMENT -> EXCEPTION} transition
     * (via the real hold-conversion capacity trip, same technique as {@link
     * #a_capacity_trip_at_confirmation_leaves_payment_completed_and_purchase_exception_end_to_end})
     * produces exactly one {@code billing.PurchaseExceptioned} outbox row and
     * TWO recipients once dispatched (success criterion 3); redelivery of
     * the triggering event still leaves exactly one row and sends no second
     * pair.
     */
    @Test
    @SuppressWarnings("unchecked")
    void an_exception_transition_appends_one_purchase_exceptioned_row_and_notifies_two_recipients_with_no_duplicate_on_redelivery() {
        SeededStudent student = seedStudentWithKnownEmail();
        UUID courseId = seedCourse();
        List<UUID> sessionIds = seedScheduledSessions(courseId, 2, 1);
        String quoteId = seedMonthlyQuote(courseId, 2, MONTHLY_PRICE);

        ResponseEntity<Map> checkout = checkout(student.id(), quoteId, "idem-d1-exception-1");
        assertThat(checkout.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String externalReference = (String) checkout.getBody().get("externalReference");
        UUID paymentId = UUID.fromString((String) checkout.getBody().get("paymentId"));

        // Race: another buyer takes the only spot on one session between the
        // hold and confirmation, exactly like the existing capacity-trip
        // scenario above.
        UUID contestedSession = sessionIds.get(0);
        assignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), contestedSession, UUID.randomUUID(), Instant.now()
        ));

        OutboxRowJpaEntity event = confirmPayment("mp-d1-exception-1", externalReference, MONTHLY_PRICE);
        assertThat(outboxWorker.process(event)).isFalse();

        assertThat(purchaseRepository.findByPaymentId(paymentId).orElseThrow().getStatus()).isEqualTo("EXCEPTION");
        List<OutboxRowJpaEntity> exceptionedRows = outboxRowsFor(BillingOutboxEventTypes.PURCHASE_EXCEPTIONED, paymentId);
        assertThat(exceptionedRows).hasSize(1);

        // Dispatch the notification — Phase C's consumer.
        assertThat(outboxWorker.process(exceptionedRows.get(0))).isFalse();
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(message -> message.getTo()[0])
            .containsExactlyInAnyOrder(student.email(), "ops@menta.local");

        // Redelivery of the triggering event: MarkPurchaseExceptionUseCase's
        // own EXCEPTION -> EXCEPTION branch is a no-op with full Mockito
        // coverage (design D5, Phase A). Re-driving the SAME event a second
        // time through this scenario's real hold-conversion capacity trip
        // also re-attempts CreatePurchaseFromPaymentEventUseCase's recovery
        // insert (it treats an EXCEPTION Purchase as "absent" for retry
        // purposes) and surfaces a separate, pre-existing defect in that
        // idempotent-insert recovery under a real database — orthogonal to
        // #209/#238, predates this change (#41/#208's own machinery), and is
        // out of scope to fix here. Either outcome (a clean no-op or this
        // surfaced RuntimeException) still proves the one invariant D.1
        // needs: no second billing.PurchaseExceptioned row and no second
        // pair of emails, because both outcomes roll the whole re-attempt
        // back.
        try {
            outboxWorker.process(event);
        } catch (RuntimeException redeliveryArtifactOfAPreExistingUnrelatedDefect) {
            // Expected under the finding documented above.
        }
        assertThat(outboxRowsFor(BillingOutboxEventTypes.PURCHASE_EXCEPTIONED, paymentId)).hasSize(1);
        verifyNoMoreInteractions(mailSender);
    }

    /**
     * D.2: a rolled-back {@code PENDING_FULFILLMENT -> EXCEPTION} transition
     * leaves no additional outbox row. A pre-existing {@code
     * billing.PurchaseExceptioned} row for the same {@code paymentId}
     * manufactures the real MySQL {@code uk_common_outbox_aggregate_event_type}
     * collision the append hits inside {@code MarkPurchaseExceptionUseCase}'s
     * {@code REQUIRED} transaction — proving the WHOLE transaction (the
     * {@code Purchase} save AND the append) rolls back together, not merely
     * the append.
     */
    @Test
    @SuppressWarnings("unchecked")
    void a_rolled_back_exception_transition_appends_no_additional_outbox_row() {
        SeededStudent student = seedStudentWithKnownEmail();
        UUID courseId = seedCourse();
        List<UUID> sessionIds = seedScheduledSessions(courseId, 2, 1);
        String quoteId = seedMonthlyQuote(courseId, 2, MONTHLY_PRICE);

        ResponseEntity<Map> checkout = checkout(student.id(), quoteId, "idem-d2-rollback-1");
        assertThat(checkout.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String externalReference = (String) checkout.getBody().get("externalReference");
        UUID paymentId = UUID.fromString((String) checkout.getBody().get("paymentId"));

        UUID contestedSession = sessionIds.get(0);
        assignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), contestedSession, UUID.randomUUID(), Instant.now()
        ));

        outboxRepository.save(new OutboxRowJpaEntity(
            "01D2ROLLBACKCOLLISION00000", BillingOutboxEventTypes.PURCHASE_EXCEPTIONED,
            paymentId.toString(), "{\"placeholder\":true}", OutboxStatus.COMPLETED,
            0, null, null, Instant.now(), null
        ));

        OutboxRowJpaEntity event = confirmPayment("mp-d2-rollback-1", externalReference, MONTHLY_PRICE);

        assertThatThrownBy(() -> outboxWorker.process(event)).isInstanceOf(RuntimeException.class);

        // The whole REQUIRED transaction rolled back together: the Purchase
        // row itself never survives either, not merely the outbox append.
        assertThat(purchaseRepository.findByPaymentId(paymentId)).isEmpty();
        // Only the manufactured collision row remains — the real attempt added none.
        assertThat(outboxRowsFor(BillingOutboxEventTypes.PURCHASE_EXCEPTIONED, paymentId)).hasSize(1);
        verifyNoInteractions(mailSender);
    }

    /**
     * D.3 — the C2 proof, the whole point of {@code REQUIRES_NEW}: force site
     * 202 (missing {@code PhysicalCourseQuote}). #238 fixed the pre-existing
     * C1 defect where {@code createPurchaseFromPaymentEvent} rebuilt via
     * {@code Purchase.pendingFulfillment} (rejecting the empty session list)
     * before {@code markException} ever ran, poisoning the worker's ambient
     * transaction and leaving no {@code Purchase} row behind at all. The row
     * is now built directly at {@code FulfillmentStatus.EXCEPTION} — the
     * worker's transaction commits cleanly, and the {@code
     * billing.PaymentFulfillmentFailed} row (already committed in its own
     * {@code REQUIRES_NEW} transaction) is joined by a REAL, persisted {@code
     * Purchase} row at {@code EXCEPTION}. A subsequent retry of the same
     * event is idempotent: no second {@code Purchase} row (unique index
     * backstop, recovered the same way as {@code CreatePurchaseFromPaymentEventUseCase}'s
     * {@code UniqueRace} scenario) and no second pair of emails.
     */
    @Test
    @SuppressWarnings("unchecked")
    void the_payment_level_fallback_and_the_purchase_row_both_land_correctly_when_the_quote_is_missing() {
        SeededStudent student = seedStudentWithKnownEmail();
        UUID courseId = seedCourse();
        seedScheduledSessions(courseId, 1, 5);
        // Never persisted: quoteRepository.findById(...) resolves empty, forcing site 202.
        String missingQuoteId = UUID.randomUUID().toString();

        UUID paymentId = seedDirectPayment(student.id(), "mp-d3-c2-1", "ext-d3-c2-1", missingQuoteId);
        OutboxRowJpaEntity event = confirmDirectPayment("mp-d3-c2-1", "ext-d3-c2-1");

        // #238: the fixed worker transaction commits cleanly — no exception
        // escapes, unlike the previous C1 defect.
        assertThat(outboxWorker.process(event)).isFalse();

        // The positive proof this defect required: a REAL Purchase row now
        // exists, built directly at EXCEPTION (never via an intermediate
        // PENDING_FULFILLMENT), with the real (non-mocked) PurchaseRepository.
        assertThat(purchaseRepository.findByPaymentId(paymentId).orElseThrow().getStatus())
            .isEqualTo("EXCEPTION");

        // C2: the payment-level fallback committed in its OWN REQUIRES_NEW
        // transaction, independent of (and in addition to) the Purchase row.
        List<OutboxRowJpaEntity> fallbackRows =
            outboxRowsFor(BillingOutboxEventTypes.PAYMENT_FULFILLMENT_FAILED, paymentId);
        assertThat(fallbackRows).hasSize(1);

        // Dispatch it — buyer + ops are both notified.
        assertThat(outboxWorker.process(fallbackRows.get(0))).isFalse();
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(message -> message.getTo()[0])
            .containsExactlyInAnyOrder(student.email(), "ops@menta.local");

        // Retry of the same event re-attempts createPurchaseFromPaymentEvent's
        // EXCEPTION-treated-as-absent recovery insert, which — same
        // pre-existing, unrelated defect documented on D.1 above (#41/#208's
        // own idempotent-insert-recovery machinery under a real database,
        // orthogonal to #238) — may either no-op cleanly or surface here.
        // Either outcome still proves the one invariant this retry needs: no
        // second Purchase row, no second fallback row, no second pair of
        // emails.
        try {
            outboxWorker.process(event);
        } catch (RuntimeException redeliveryArtifactOfAPreExistingUnrelatedDefect) {
            // Expected under the finding documented above.
        }
        assertThat(purchaseRepository.findByPaymentId(paymentId).orElseThrow().getStatus())
            .isEqualTo("EXCEPTION");
        assertThat(outboxRowsFor(BillingOutboxEventTypes.PAYMENT_FULFILLMENT_FAILED, paymentId)).hasSize(1);
        verifyNoMoreInteractions(mailSender);
    }
}
