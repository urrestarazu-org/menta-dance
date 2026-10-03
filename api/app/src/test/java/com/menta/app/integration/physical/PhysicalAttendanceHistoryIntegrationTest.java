package com.menta.app.integration.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.menta.app.integration.support.AbstractPhysicalMySqlIntegrationTest;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
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

/**
 * HTTP-level coverage for the self attendance-history endpoint (#39, US-PHYSICAL-002, P1) —
 * through the real {@code SecurityConfig} filter chain, {@code GetPhysicalAttendanceHistoryUseCase}
 * bean and the real {@code physical.attendance.zone-id} default, mirrors
 * {@code PhysicalCheckInIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class PhysicalAttendanceHistoryIntegrationTest extends AbstractPhysicalMySqlIntegrationTest {

    /** R2 remediation fixture (#39 verify-report FAIL finding 1) — mirrors {@code PhysicalPurchaseIntegrationTest}. */
    private static final String MERCHANT_ACCOUNT_ID = "merchant-integration-attendance-history";
    private static final BigDecimal MONTHLY_PRICE = new BigDecimal("300.00");

    // Time-bomb fix (#45 follow-up): every seeded session date is computed relative to the
    // real clock at test-run time instead of a hardcoded absolute calendar date, so the suite
    // can never flake just because "now" caught up with a literal that used to be safely in
    // the future. FIRST_MONTH is pinned to NEXT month — far enough ahead of normal test runtime
    // (earliest seeded session is day 3) yet inside CoveragePlanner.COVERAGE_LOOKAHEAD (120
    // days): the monthly-purchase scenario checks out against SECOND_MONTH's sessions, so the
    // farthest one (SECOND_MONTH day 5, at most ~65 days away) must stay within that horizon.
    // A 3-month offset broke the suite during the first days of every month (81/366 days).
    // SECOND_MONTH/EMPTY_MONTH are derived from it so every month-query string stays in sync
    // with the dates it describes.
    private static final YearMonth FIRST_MONTH = YearMonth.from(LocalDate.now(ZoneOffset.UTC).plusMonths(1));
    private static final YearMonth SECOND_MONTH = FIRST_MONTH.plusMonths(1);
    private static final YearMonth EMPTY_MONTH = FIRST_MONTH.plusMonths(2);

    private static Instant instantAt(LocalDate date) {
        return date.atTime(LocalTime.of(22, 0)).atZone(ZoneOffset.UTC).toInstant();
    }

    private static Instant instantAt(YearMonth month, int dayOfMonth) {
        return instantAt(month.atDay(dayOfMonth));
    }

    private static String monthQuery(YearMonth month) {
        return month.toString();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
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

    /** #45, US-PHYSICAL-008 (D2/C9): a MANUAL row carries the receptionist's userId as deviceId. */
    private void seedManualAttendance(
        UUID sessionId, UUID studentId, Instant recordedAt, UUID receptionistId
    ) {
        attendanceRepository.save(new AttendanceJpaEntity(
            UUID.randomUUID(), sessionId, studentId, recordedAt, receptionistId.toString(), "MANUAL"
        ));
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
            instantAt(FIRST_MONTH, 3), instantAt(FIRST_MONTH, 10),
            instantAt(FIRST_MONTH, 17), instantAt(FIRST_MONTH, 24),
            instantAt(FIRST_MONTH, 28)
        );
        for (int i = 0; i < scheduledAtValues.size(); i++) {
            UUID sessionId = seedSession(courseId, scheduledAtValues.get(i));
            seedAssignment(sessionId, studentId);
            if (i < 4) {
                seedAttendance(sessionId, studentId, scheduledAtValues.get(i).plusSeconds(120));
            }
        }

        ResponseEntity<Map> response = readOwnMonth(studentId, monthQuery(FIRST_MONTH), true);

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
            instantAt(SECOND_MONTH, 2), instantAt(SECOND_MONTH, 9),
            instantAt(SECOND_MONTH, 16), instantAt(SECOND_MONTH, 23)
        )) {
            UUID sessionId = seedSession(courseId, scheduledAt);
            seedAssignment(sessionId, studentId);
            seedAttendance(sessionId, studentId, scheduledAt.plusSeconds(120));
        }

        ResponseEntity<Map> response = readOwnMonth(studentId, monthQuery(SECOND_MONTH), true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("scheduledSessionCount")).isEqualTo(4);
        assertThat(response.getBody().get("attended")).isEqualTo(4);
        assertThat(response.getBody().get("absent")).isEqualTo(0);
    }

    /**
     * #45, US-PHYSICAL-008 (P4, D2 consumer risk): a MANUAL row renders identically to a QR row
     * in the history view — only {@code kind} differs, and it counts toward {@code attended}
     * the same way, proving the broadened {@code device_id} "actor" invariant does not leak into
     * this read model.
     */
    @Test
    void a_manual_attendance_row_renders_correctly_in_the_history_view() {
        UUID studentId = issueUser(Role.STUDENT);
        UUID receptionistId = issueUser(Role.RECEPTIONIST);
        UUID courseId = seedCourse();
        UUID sessionId = seedSession(courseId, instantAt(FIRST_MONTH, 7));
        seedAssignment(sessionId, studentId);
        seedManualAttendance(
            sessionId, studentId, instantAt(FIRST_MONTH, 7).plusSeconds(300), receptionistId
        );

        ResponseEntity<Map> response = readOwnMonth(studentId, monthQuery(FIRST_MONTH), true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("scheduledSessionCount")).isEqualTo(1);
        assertThat(response.getBody().get("attended")).isEqualTo(1);
        assertThat(response.getBody().get("absent")).isEqualTo(0);
        assertThat((List) response.getBody().get("sessions")).hasSize(1);
    }

    @Test
    void a_month_with_zero_assignments_returns_200_zeroed_never_404() {
        UUID studentId = issueUser(Role.STUDENT);

        ResponseEntity<Map> response = readOwnMonth(studentId, monthQuery(EMPTY_MONTH), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("scheduledSessionCount")).isEqualTo(0);
        assertThat(response.getBody().get("attendanceRate")).isEqualTo(0.00);
        assertThat((List) response.getBody().get("sessions")).isEmpty();
    }

    @Test
    void include_absent_toggles_only_the_list_never_the_aggregates_against_real_rows() {
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        UUID attendedSession = seedSession(courseId, instantAt(FIRST_MONTH, 3));
        UUID absentSession = seedSession(courseId, instantAt(FIRST_MONTH, 10));
        seedAssignment(attendedSession, studentId);
        seedAssignment(absentSession, studentId);
        seedAttendance(attendedSession, studentId, instantAt(FIRST_MONTH, 3).plusSeconds(300));

        ResponseEntity<Map> withAbsent = readOwnMonth(studentId, monthQuery(FIRST_MONTH), true);
        ResponseEntity<Map> withoutAbsent = readOwnMonth(studentId, monthQuery(FIRST_MONTH), false);

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
        // 2 sessions in FIRST_MONTH, 1 in SECOND_MONTH — all computed relative to "now" so they
        // stay safely in the future and CoveragePlanner claims them regardless of run date.
        LocalDate firstMonthLastDay = FIRST_MONTH.atEndOfMonth();
        UUID septemberFirst = seedSession(courseId, instantAt(firstMonthLastDay.minusDays(1)));
        UUID septemberSecond = seedSession(courseId, instantAt(firstMonthLastDay));
        UUID octoberFirst = seedSession(courseId, instantAt(SECOND_MONTH, 5));
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

        ResponseEntity<Map> september = readOwnMonth(studentId, monthQuery(FIRST_MONTH), true);
        ResponseEntity<Map> october = readOwnMonth(studentId, monthQuery(SECOND_MONTH), true);

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
        UUID realSession = seedSession(courseId, instantAt(FIRST_MONTH, 14));
        seedAssignment(realSession, studentId);
        seedAttendance(realSession, studentId, instantAt(FIRST_MONTH, 14).plusSeconds(300));
        // Cancelled before any student ever quoted it — no assignment row was ever created.
        seedCancelledSession(courseId, instantAt(FIRST_MONTH, 21));

        ResponseEntity<Map> response = readOwnMonth(studentId, monthQuery(FIRST_MONTH), true);

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
            UUID sessionId = seedSession(courseId, instantAt(FIRST_MONTH, day));
            seedAssignment(sessionId, studentId);
        }

        ResponseEntity<Map> response = readOwnMonth(studentId, monthQuery(FIRST_MONTH), true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("scheduledSessionCount")).isEqualTo(totalSessions);
        assertThat((List) response.getBody().get("sessions")).hasSize(totalSessions);
    }

    @Test
    void an_anonymous_request_is_rejected_with_401() {
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/physical/attendance/me?month=" + monthQuery(FIRST_MONTH), HttpMethod.GET, HttpEntity.EMPTY,
            Map.class
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
            UUID sessionId = seedSession(courseId, instantAt(FIRST_MONTH, 5));
            seedAssignment(sessionId, studentId);
        }
        // two more sessions to reach five total assignments across the three courses.
        UUID sessionD = seedSession(courseA, instantAt(FIRST_MONTH, 12));
        seedAssignment(sessionD, studentId);
        UUID sessionE = seedSession(courseB, instantAt(FIRST_MONTH, 19));
        seedAssignment(sessionE, studentId);

        ResponseEntity<Map> response = readElevatedMonth(adminId, Role.ADMIN, studentId, monthQuery(FIRST_MONTH));

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
            instantAt(FIRST_MONTH, 3), instantAt(FIRST_MONTH, 10),
            instantAt(FIRST_MONTH, 17)
        )) {
            UUID sessionId = seedSession(ownCourse, scheduledAt);
            seedAssignment(sessionId, studentId);
            seedAttendance(sessionId, studentId, scheduledAt.plusSeconds(120));
        }
        for (Instant scheduledAt : List.of(
            instantAt(FIRST_MONTH, 5), instantAt(FIRST_MONTH, 12)
        )) {
            UUID sessionId = seedSession(otherCourse, scheduledAt);
            seedAssignment(sessionId, studentId);
        }

        ResponseEntity<Map> response =
            readElevatedMonth(instructorId, Role.INSTRUCTOR, studentId, monthQuery(FIRST_MONTH));

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

        ResponseEntity<Map> response =
            readElevatedMonth(studentCallerId, Role.STUDENT, targetStudentId, monthQuery(FIRST_MONTH));

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
        UUID sessionId = seedSession(otherCourse, instantAt(FIRST_MONTH, 5));
        seedAssignment(sessionId, studentX);

        ResponseEntity<Map> responseForX =
            readElevatedMonth(instructorId, Role.INSTRUCTOR, studentX, monthQuery(FIRST_MONTH));
        ResponseEntity<Map> responseForY =
            readElevatedMonth(instructorId, Role.INSTRUCTOR, studentY, monthQuery(FIRST_MONTH));

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
            UUID sessionId = seedSession(courseId, instantAt(FIRST_MONTH, day));
            seedAssignment(sessionId, studentId);
        }

        ResponseEntity<Map> response =
            readElevatedMonth(adminId, Role.ADMIN, studentId, monthQuery(FIRST_MONTH));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("scheduledSessionCount")).isEqualTo(totalSessions);
        assertThat((List) response.getBody().get("sessions")).hasSize(totalSessions);
    }

    @Test
    void an_anonymous_request_to_the_elevated_endpoint_is_rejected_with_401() {
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/attendance/" + UUID.randomUUID() + "?month=" + monthQuery(FIRST_MONTH),
            HttpMethod.GET, HttpEntity.EMPTY, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
