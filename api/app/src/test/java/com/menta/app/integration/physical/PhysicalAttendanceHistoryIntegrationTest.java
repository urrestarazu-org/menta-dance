package com.menta.app.integration.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.menta.billing.application.dto.PaymentPreferenceResult;
import com.menta.billing.application.dto.ProviderPaymentResult;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.domain.model.Money;
import com.menta.billing.infrastructure.persistence.entity.PhysicalCourseQuoteJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.WebhookInboxJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PhysicalCourseQuoteJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseSessionJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.WebhookInboxJpaRepository;
import com.menta.billing.infrastructure.webhook.WebhookInboxStatus;
import com.menta.billing.infrastructure.webhook.WebhookVerificationWorker;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.AttendanceJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.AttendanceJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import com.menta.shared.domain.vo.Email;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
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
 * HTTP-level coverage for the self attendance-history endpoint (#39, US-PHYSICAL-002, P1) —
 * through the real {@code SecurityConfig} filter chain, {@code GetPhysicalAttendanceHistoryUseCase}
 * bean and the real {@code physical.attendance.zone-id} default, mirrors
 * {@code PhysicalCheckInIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class PhysicalAttendanceHistoryIntegrationTest {

    /** R2 remediation fixture (#39 verify-report FAIL finding 1) — mirrors {@code PhysicalPurchaseIntegrationTest}. */
    private static final String MERCHANT_ACCOUNT_ID = "merchant-integration-attendance-history";
    private static final BigDecimal MONTHLY_PRICE = new BigDecimal("300.00");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
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
    @Autowired private PhysicalCourseJpaRepository courseRepository;
    @Autowired private PhysicalSessionJpaRepository sessionRepository;
    @Autowired private PhysicalCapacityAssignmentJpaRepository assignmentRepository;
    @Autowired private AttendanceJpaRepository attendanceRepository;

    // R2 remediation fixture: real MONTHLY purchase checkout + confirmation flow.
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private PurchaseJpaRepository purchaseRepository;
    @Autowired private PurchaseSessionJpaRepository purchaseSessionRepository;
    @Autowired private WebhookInboxJpaRepository inboxRepository;
    @Autowired private OutboxRowJpaRepository outboxRepository;
    @Autowired private PhysicalCourseQuoteJpaRepository quoteRepository;
    @Autowired private WebhookVerificationWorker webhookWorker;
    @Autowired private OutboxReconciliationWorker outboxWorker;

    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private BankTransferRateLimitPort bankTransferRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;
    @MockBean private PaymentPreferencePort paymentPreferencePort;
    @MockBean private PaymentProviderPort paymentProviderPort;

    @SuppressWarnings("rawtypes")
    @MockBean
    private RedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        purchaseSessionRepository.deleteAll();
        purchaseRepository.deleteAll();
        outboxRepository.deleteAll();
        inboxRepository.deleteAll();
        paymentRepository.deleteAll();
        quoteRepository.deleteAll();
        attendanceRepository.deleteAll();
        assignmentRepository.deleteAll();
        sessionRepository.deleteAll();
        courseRepository.deleteAll();
    }

    private UUID issueUser(Role role) {
        User user = User.create(
            Email.of(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com"),
            "irrelevant-hash", role
        );
        userRepository.save(user);
        when(tokenBlacklistPort.isBlacklisted(anyString())).thenReturn(false);
        when(tokenBlacklistPort.currentTokenVersion(anyString()))
            .thenReturn(java.util.OptionalLong.empty());
        return user.getId().getValue();
    }

    private String tokenFor(UUID userId, Role role) {
        User user = new User(
            UserId.of(userId), Email.of("token@example.com"), "hash", role, UserStatus.ACTIVE,
            java.time.LocalDateTime.now(), java.time.LocalDateTime.now()
        );
        return accessTokenIssuer.issue(user).token();
    }

    private UUID seedCourse() {
        return seedCourse(UUID.randomUUID());
    }

    private UUID seedCourse(UUID professorId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new PhysicalCourseJpaEntity(
            id, "Salsa Intermedio", "desc", professorId, "Ana Perez", "WEDNESDAY",
            LocalTime.of(20, 0), 60, "INTERMEDIATE", 20, CourseStatus.ACTIVE, now, now
        ));
        return id;
    }

    private UUID seedSession(UUID courseId, Instant scheduledAt) {
        UUID id = UUID.randomUUID();
        sessionRepository.save(new PhysicalSessionJpaEntity(id, courseId, scheduledAt, 20, "SCHEDULED", null));
        return id;
    }

    private void seedAssignment(UUID sessionId, UUID studentId) {
        assignmentRepository.save(
            new PhysicalCapacityAssignmentJpaEntity(UUID.randomUUID(), sessionId, studentId, Instant.now())
        );
    }

    private void seedAttendance(UUID sessionId, UUID studentId, Instant recordedAt) {
        attendanceRepository.save(
            new AttendanceJpaEntity(UUID.randomUUID(), sessionId, studentId, recordedAt, "reader-1", "QR")
        );
    }

    /** R2 remediation fixture: cancelled BEFORE ever being quoted — no assignment row exists. */
    private UUID seedCancelledSession(UUID courseId, Instant scheduledAt) {
        UUID id = UUID.randomUUID();
        sessionRepository.save(new PhysicalSessionJpaEntity(id, courseId, scheduledAt, 20, "CANCELLED", null));
        return id;
    }

    // --- R2 remediation fixture: real MONTHLY purchase checkout + confirmation (mirrors
    // PhysicalPurchaseIntegrationTest.monthly_purchase_confirmed_assigns_every_covered_session_end_to_end) ---

    private HttpHeaders checkoutHeadersFor(UUID studentId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenFor(studentId, Role.STUDENT));
        return headers;
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

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> checkout(UUID studentId, String quoteId, String idempotencyKey) {
        Map<String, Object> body = new HashMap<>();
        body.put("quoteId", quoteId);
        body.put("paymentMethod", "MERCADO_PAGO");
        body.put("idempotencyKey", idempotencyKey);
        return http.exchange(
            "/api/v1/billing/physical/purchases", HttpMethod.POST,
            new HttpEntity<>(body, checkoutHeadersFor(studentId)), Map.class
        );
    }

    /** Drives the confirmation half through the real webhook worker, mirroring PhysicalPurchaseIntegrationTest. */
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
            .reduce((first, second) -> second)
            .orElseThrow(() -> new IllegalStateException("No billing.PhysicalPaymentCompleted outbox row was produced"));
    }

    private ResponseEntity<Map> readOwnMonth(UUID studentId, String month, Boolean includeAbsent) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(studentId, Role.STUDENT));
        String query = "?month=" + month + (includeAbsent == null ? "" : "&includeAbsent=" + includeAbsent);
        return http.exchange(
            "/api/v1/physical/attendance/me" + query, HttpMethod.GET, new HttpEntity<>(headers), Map.class
        );
    }

    @Test
    void a_five_session_month_with_one_absence_round_trips_through_the_full_stack() {
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        List<Instant> scheduledAtValues = List.of(
            Instant.parse("2026-09-03T22:00:00Z"), Instant.parse("2026-09-10T22:00:00Z"),
            Instant.parse("2026-09-17T22:00:00Z"), Instant.parse("2026-09-24T22:00:00Z"),
            Instant.parse("2026-09-28T22:00:00Z")
        );
        for (int i = 0; i < scheduledAtValues.size(); i++) {
            UUID sessionId = seedSession(courseId, scheduledAtValues.get(i));
            seedAssignment(sessionId, studentId);
            if (i < 4) {
                seedAttendance(sessionId, studentId, scheduledAtValues.get(i).plusSeconds(120));
            }
        }

        ResponseEntity<Map> response = readOwnMonth(studentId, "2026-09", true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("scheduledSessionCount")).isEqualTo(5);
        assertThat(response.getBody().get("attended")).isEqualTo(4);
        assertThat(response.getBody().get("absent")).isEqualTo(1);
        assertThat(response.getBody().get("attendanceRate")).isEqualTo(80.00);
        assertThat((List) response.getBody().get("sessions")).hasSize(5);
    }

    @Test
    void a_four_session_month_all_attended_round_trips_through_the_full_stack() {
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        for (Instant scheduledAt : List.of(
            Instant.parse("2026-10-02T22:00:00Z"), Instant.parse("2026-10-09T22:00:00Z"),
            Instant.parse("2026-10-16T22:00:00Z"), Instant.parse("2026-10-23T22:00:00Z")
        )) {
            UUID sessionId = seedSession(courseId, scheduledAt);
            seedAssignment(sessionId, studentId);
            seedAttendance(sessionId, studentId, scheduledAt.plusSeconds(120));
        }

        ResponseEntity<Map> response = readOwnMonth(studentId, "2026-10", true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("scheduledSessionCount")).isEqualTo(4);
        assertThat(response.getBody().get("attended")).isEqualTo(4);
        assertThat(response.getBody().get("absent")).isEqualTo(0);
    }

    @Test
    void a_month_with_zero_assignments_returns_200_zeroed_never_404() {
        UUID studentId = issueUser(Role.STUDENT);

        ResponseEntity<Map> response = readOwnMonth(studentId, "2026-11", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("scheduledSessionCount")).isEqualTo(0);
        assertThat(response.getBody().get("attendanceRate")).isEqualTo(0.00);
        assertThat((List) response.getBody().get("sessions")).isEmpty();
    }

    @Test
    void include_absent_toggles_only_the_list_never_the_aggregates_against_real_rows() {
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        UUID attendedSession = seedSession(courseId, Instant.parse("2026-09-03T22:00:00Z"));
        UUID absentSession = seedSession(courseId, Instant.parse("2026-09-10T22:00:00Z"));
        seedAssignment(attendedSession, studentId);
        seedAssignment(absentSession, studentId);
        seedAttendance(attendedSession, studentId, Instant.parse("2026-09-03T22:05:00Z"));

        ResponseEntity<Map> withAbsent = readOwnMonth(studentId, "2026-09", true);
        ResponseEntity<Map> withoutAbsent = readOwnMonth(studentId, "2026-09", false);

        assertThat((List) withAbsent.getBody().get("sessions")).hasSize(2);
        assertThat((List) withoutAbsent.getBody().get("sessions")).hasSize(1);
        assertThat(withoutAbsent.getBody().get("scheduledSessionCount"))
            .isEqualTo(withAbsent.getBody().get("scheduledSessionCount"));
        assertThat(withoutAbsent.getBody().get("attended")).isEqualTo(withAbsent.getBody().get("attended"));
        assertThat(withoutAbsent.getBody().get("absent")).isEqualTo(withAbsent.getBody().get("absent"));
    }

    /**
     * Verify-report remediation, CRITICAL finding 1 (spec "Denominator counts assignments, not
     * purchase coverage windows" — scenario "A mid-month MONTHLY purchase splits across two
     * monthly views"). Drives a real checkout + webhook confirmation through the full Billing
     * stack (mirrors {@code PhysicalPurchaseIntegrationTest.monthly_purchase_confirmed_assigns_every_covered_session_end_to_end}),
     * covering 3 sessions split across September and October. Asserts each month's
     * {@code scheduledSessionCount} reflects only that month's assigned sessions, and that
     * neither month's count equals the purchase's own coverage-window session count (3).
     */
    @Test
    @SuppressWarnings("unchecked")
    void a_mid_month_monthly_purchase_splits_assignments_across_two_monthly_views() {
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        // 2 sessions in September, 1 in October — all after "now" so CoveragePlanner claims them.
        UUID septemberFirst = seedSession(courseId, Instant.parse("2026-09-28T22:00:00Z"));
        UUID septemberSecond = seedSession(courseId, Instant.parse("2026-09-29T22:00:00Z"));
        UUID octoberFirst = seedSession(courseId, Instant.parse("2026-10-05T22:00:00Z"));
        String quoteId = seedMonthlyQuote(courseId, 3, MONTHLY_PRICE);

        when(paymentPreferencePort.createPreference(any())).thenReturn(
            new PaymentPreferenceResult("pref-attendance-r2", "https://mp.example/checkout/pref-attendance-r2")
        );
        ResponseEntity<Map> checkoutResponse = checkout(studentId, quoteId, "idem-attendance-r2-1");
        assertThat(checkoutResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String externalReference = (String) checkoutResponse.getBody().get("externalReference");
        UUID paymentId = UUID.fromString((String) checkoutResponse.getBody().get("paymentId"));

        OutboxRowJpaEntity event = confirmPayment("mp-attendance-r2-1", externalReference, MONTHLY_PRICE);
        outboxWorker.process(event);

        var purchase = purchaseRepository.findByPaymentId(paymentId).orElseThrow();
        int purchaseCoverageWindowCount =
            purchaseSessionRepository.findByPurchaseIdOrderByPositionAsc(purchase.getId()).size();
        assertThat(purchaseCoverageWindowCount).isEqualTo(3);
        assertThat(assignmentRepository.countBySessionId(septemberFirst)).isEqualTo(1);
        assertThat(assignmentRepository.countBySessionId(septemberSecond)).isEqualTo(1);
        assertThat(assignmentRepository.countBySessionId(octoberFirst)).isEqualTo(1);

        ResponseEntity<Map> september = readOwnMonth(studentId, "2026-09", true);
        ResponseEntity<Map> october = readOwnMonth(studentId, "2026-10", true);

        assertThat(september.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(september.getBody().get("scheduledSessionCount")).isEqualTo(2);
        assertThat((List) september.getBody().get("sessions")).hasSize(2);
        assertThat(october.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(october.getBody().get("scheduledSessionCount")).isEqualTo(1);
        assertThat((List) october.getBody().get("sessions")).hasSize(1);
        assertThat(september.getBody().get("scheduledSessionCount")).isNotEqualTo(purchaseCoverageWindowCount);
        assertThat(october.getBody().get("scheduledSessionCount")).isNotEqualTo(purchaseCoverageWindowCount);
    }

    /**
     * Verify-report remediation, CRITICAL finding 2 (spec "Sessions cancelled before quoting are
     * naturally absent" — scenario "A pre-quote cancellation never produced an assignment").
     * Seeds a session cancelled BEFORE any student ever quoted/booked it, so no
     * {@code physical_capacity_assignments} row was ever created for it (mirrors the
     * direct-CANCELLED-seeding idiom already used by
     * {@code PhysicalSessionManagementIntegrationTest.management_listing_includes_cancelled_sessions}).
     * Asserts the cancelled session is absent from both {@code sessions[]} and
     * {@code scheduledSessionCount} for that month.
     */
    @Test
    void a_pre_quote_cancellation_never_produced_an_assignment() {
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        UUID realSession = seedSession(courseId, Instant.parse("2026-09-14T22:00:00Z"));
        seedAssignment(realSession, studentId);
        seedAttendance(realSession, studentId, Instant.parse("2026-09-14T22:05:00Z"));
        // Cancelled before any student ever quoted it — no assignment row was ever created.
        seedCancelledSession(courseId, Instant.parse("2026-09-21T22:00:00Z"));

        ResponseEntity<Map> response = readOwnMonth(studentId, "2026-09", true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("scheduledSessionCount")).isEqualTo(1);
        assertThat((List) response.getBody().get("sessions")).hasSize(1);
        Map onlySession = (Map) ((List) response.getBody().get("sessions")).get(0);
        assertThat(onlySession.get("sessionId")).isEqualTo(realSession.toString());
    }

    /** D3/R10 (bounded-result test): every session in a full month is returned untruncated. */
    @Test
    void a_full_month_returns_every_assignment_in_a_single_response_with_no_truncation() {
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        int totalSessions = 8;
        for (int day = 1; day <= totalSessions; day++) {
            UUID sessionId = seedSession(courseId, Instant.parse("2026-09-0" + day + "T22:00:00Z"));
            seedAssignment(sessionId, studentId);
        }

        ResponseEntity<Map> response = readOwnMonth(studentId, "2026-09", true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("scheduledSessionCount")).isEqualTo(totalSessions);
        assertThat((List) response.getBody().get("sessions")).hasSize(totalSessions);
    }

    @Test
    void an_anonymous_request_is_rejected_with_401() {
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/physical/attendance/me?month=2026-09", HttpMethod.GET, HttpEntity.EMPTY, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<Map> readElevatedMonth(
        UUID callerId, Role callerRole, UUID studentId, String month
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(callerId, callerRole));
        return http.exchange(
            "/api/v1/admin/physical/attendance/" + studentId + "?month=" + month + "&includeAbsent=true",
            HttpMethod.GET, new HttpEntity<>(headers), Map.class
        );
    }

    /** R6 — ADMIN reads any student's month unrestricted, across multiple courses. */
    @Test
    void admin_reads_any_students_month_unrestricted_across_courses() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseA = seedCourse();
        UUID courseB = seedCourse();
        UUID courseC = seedCourse();
        for (UUID courseId : List.of(courseA, courseB, courseC)) {
            UUID sessionId = seedSession(courseId, Instant.parse("2026-09-05T22:00:00Z"));
            seedAssignment(sessionId, studentId);
        }
        // two more sessions to reach five total assignments across the three courses.
        UUID sessionD = seedSession(courseA, Instant.parse("2026-09-12T22:00:00Z"));
        seedAssignment(sessionD, studentId);
        UUID sessionE = seedSession(courseB, Instant.parse("2026-09-19T22:00:00Z"));
        seedAssignment(sessionE, studentId);

        ResponseEntity<Map> response = readElevatedMonth(adminId, Role.ADMIN, studentId, "2026-09");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("scheduledSessionCount")).isEqualTo(5);
        assertThat((List) response.getBody().get("sessions")).hasSize(5);
    }

    /**
     * R7 — the High-risk row's own proof: an INSTRUCTOR teaching course A reads a student with 3
     * assignments in course A and 2 in course B (taught by someone else). {@code sessions[]}
     * MUST contain only the 3 course-A sessions, and every aggregate number MUST reflect only
     * those 3, never the full 5.
     */
    @Test
    void instructor_sees_only_their_own_courses_sessions_and_aggregates() {
        UUID instructorId = issueUser(Role.INSTRUCTOR);
        UUID otherProfessorId = UUID.randomUUID();
        UUID studentId = issueUser(Role.STUDENT);
        UUID ownCourse = seedCourse(instructorId);
        UUID otherCourse = seedCourse(otherProfessorId);
        for (Instant scheduledAt : List.of(
            Instant.parse("2026-09-03T22:00:00Z"), Instant.parse("2026-09-10T22:00:00Z"),
            Instant.parse("2026-09-17T22:00:00Z")
        )) {
            UUID sessionId = seedSession(ownCourse, scheduledAt);
            seedAssignment(sessionId, studentId);
            seedAttendance(sessionId, studentId, scheduledAt.plusSeconds(120));
        }
        for (Instant scheduledAt : List.of(
            Instant.parse("2026-09-05T22:00:00Z"), Instant.parse("2026-09-12T22:00:00Z")
        )) {
            UUID sessionId = seedSession(otherCourse, scheduledAt);
            seedAssignment(sessionId, studentId);
        }

        ResponseEntity<Map> response = readElevatedMonth(instructorId, Role.INSTRUCTOR, studentId, "2026-09");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List) response.getBody().get("sessions")).hasSize(3);
        assertThat(response.getBody().get("scheduledSessionCount")).isEqualTo(3);
        assertThat(response.getBody().get("attended")).isEqualTo(3);
        assertThat(response.getBody().get("absent")).isEqualTo(0);
    }

    /** R8 — a STUDENT caller cannot reach the elevated endpoint at all. */
    @Test
    void a_student_caller_cannot_reach_the_elevated_endpoint() {
        UUID studentCallerId = issueUser(Role.STUDENT);
        UUID targetStudentId = issueUser(Role.STUDENT);

        ResponseEntity<Map> response = readElevatedMonth(studentCallerId, Role.STUDENT, targetStudentId, "2026-09");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * R9 — anti-enumeration: student X (not enrolled in any course the instructor teaches) and
     * student Y (a real student with zero physical assignments that month) both return
     * byte-identical 200 bodies via the elevated endpoint, asserted by full-body equality.
     */
    @Test
    void non_overlapping_student_and_genuinely_empty_student_are_indistinguishable() {
        UUID instructorId = issueUser(Role.INSTRUCTOR);
        UUID otherProfessorId = UUID.randomUUID();
        UUID studentX = issueUser(Role.STUDENT); // not enrolled in the instructor's course
        UUID studentY = issueUser(Role.STUDENT); // zero physical assignments at all
        UUID otherCourse = seedCourse(otherProfessorId);
        UUID sessionId = seedSession(otherCourse, Instant.parse("2026-09-05T22:00:00Z"));
        seedAssignment(sessionId, studentX);

        ResponseEntity<Map> responseForX = readElevatedMonth(instructorId, Role.INSTRUCTOR, studentX, "2026-09");
        ResponseEntity<Map> responseForY = readElevatedMonth(instructorId, Role.INSTRUCTOR, studentY, "2026-09");

        assertThat(responseForX.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseForY.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseForX.getBody()).isEqualTo(responseForY.getBody());
        assertThat(responseForX.getBody().get("scheduledSessionCount")).isEqualTo(0);
        assertThat((List) responseForX.getBody().get("sessions")).isEmpty();
        assertThat(responseForX.getBody().get("attendanceRate")).isEqualTo(0.00);
    }

    /** D3/R10 (bounded-result test, elevated path): a full month is returned untruncated. */
    @Test
    void a_full_month_returns_every_assignment_untruncated_via_the_elevated_endpoint() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        int totalSessions = 8;
        for (int day = 1; day <= totalSessions; day++) {
            UUID sessionId = seedSession(courseId, Instant.parse("2026-09-0" + day + "T22:00:00Z"));
            seedAssignment(sessionId, studentId);
        }

        ResponseEntity<Map> response = readElevatedMonth(adminId, Role.ADMIN, studentId, "2026-09");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("scheduledSessionCount")).isEqualTo(totalSessions);
        assertThat((List) response.getBody().get("sessions")).hasSize(totalSessions);
    }

    @Test
    void an_anonymous_request_to_the_elevated_endpoint_is_rejected_with_401() {
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/attendance/" + UUID.randomUUID() + "?month=2026-09",
            HttpMethod.GET, HttpEntity.EMPTY, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
