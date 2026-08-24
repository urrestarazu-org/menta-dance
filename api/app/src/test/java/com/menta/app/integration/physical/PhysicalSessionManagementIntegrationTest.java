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
import com.menta.billing.infrastructure.webhook.WebhookVerificationWorker;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import com.menta.shared.domain.vo.Email;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
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
 * HTTP-level coverage for US-PHYSICAL-006's session management endpoints,
 * through the real {@code SecurityConfig} filter chain — this is what
 * proves the new {@code /api/v1/admin/physical/sessions/**} matcher (a
 * DIFFERENT top-level prefix than #42's {@code
 * /api/v1/admin/physical/courses/**}) is declared before the generic
 * {@code /api/v1/admin/**} rule.
 *
 * <p>Real MySQL, not the H2-backed {@code test} profile #42 used: {@code
 * PhysicalSessionAvailabilityProjection#getScheduledAt()} is {@code Instant},
 * and H2's native-query driver hands Spring Data's projection proxy a raw
 * {@code OffsetDateTime} it cannot convert — a confirmed H2-only asymmetry
 * (this project's H2 version/config), not a real MySQL behavior. #42 never
 * hit this because its flows never go through a native-query projection.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class PhysicalSessionManagementIntegrationTest {

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
    }

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private PhysicalCourseJpaRepository courseRepository;
    @Autowired private PhysicalSessionJpaRepository sessionRepository;
    @Autowired private PhysicalCapacityAssignmentJpaRepository assignmentRepository;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private PurchaseJpaRepository purchaseRepository;
    @Autowired private WebhookInboxJpaRepository inboxRepository;
    @Autowired private OutboxRowJpaRepository outboxRepository;
    @Autowired private WebhookVerificationWorker webhookWorker;
    @Autowired private OutboxReconciliationWorker outboxWorker;

    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;
    @MockBean private PaymentProviderPort paymentProviderPort;

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

    private UUID issueUser(Role role) {
        User user = User.create(Email.of(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com"),
            "irrelevant-hash", role);
        userRepository.save(user);
        when(tokenBlacklistPort.isBlacklisted(anyString())).thenReturn(false);
        when(tokenBlacklistPort.currentTokenVersion(anyString())).thenReturn(java.util.OptionalLong.empty());
        return user.getId().getValue();
    }

    private String tokenFor(UUID userId, Role role) {
        User user = new User(
            UserId.of(userId), Email.of("token@example.com"), "hash", role, UserStatus.ACTIVE,
            java.time.LocalDateTime.now(), java.time.LocalDateTime.now()
        );
        return accessTokenIssuer.issue(user).token();
    }

    private HttpEntity<Map<String, Object>> authenticated(UUID userId, Role role, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenFor(userId, role));
        return new HttpEntity<>(body, headers);
    }

    private UUID seedCourse(UUID professorId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new PhysicalCourseJpaEntity(
            id, "Salsa inicial", "desc", professorId, "María García", "WEDNESDAY", LocalTime.of(20, 0),
            60, "INTERMEDIATE", 20, CourseStatus.ACTIVE, now, now
        ));
        return id;
    }

    private UUID seedSession(UUID courseId, Instant scheduledAt, int capacity, String status) {
        UUID id = UUID.randomUUID();
        sessionRepository.save(new PhysicalSessionJpaEntity(id, courseId, scheduledAt, capacity, status, null));
        return id;
    }

    private UUID seedPendingPhysicalPayment(String providerPaymentId, UUID studentId, UUID sessionId) {
        UUID paymentId = UUID.randomUUID();
        paymentRepository.save(new PaymentJpaEntity(
            paymentId, studentId, providerPaymentId, new BigDecimal("100.00"), "ARS",
            "ext-" + providerPaymentId, "merchant-1", "PHYSICAL", sessionId.toString(),
            "AWAITING_PROVIDER", null, null, Instant.now()
        ));
        return paymentId;
    }

    private void verifyAndDispatch(String providerPaymentId) {
        WebhookInboxJpaEntity inbox = inboxRepository.save(new WebhookInboxJpaEntity(
            providerPaymentId + ":req-1", providerPaymentId, "req-1",
            com.menta.billing.infrastructure.webhook.WebhookInboxStatus.RECEIVED,
            0, null, null, Instant.now(), null
        ));
        webhookWorker.process(inbox);
        OutboxRowJpaEntity outbox = outboxRepository.findAll().getLast();
        assertThat(outboxWorker.process(outbox)).isFalse();
    }

    private ResponseEntity<Map> issueAccessQr(UUID sessionId, UUID studentId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(studentId, Role.STUDENT));
        return http.exchange(
            "/api/v1/physical/sessions/" + sessionId + "/access-qr", HttpMethod.POST,
            new HttpEntity<>(headers), Map.class
        );
    }

    @Test
    void student_is_rejected_before_reaching_the_patch_endpoint() {
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID sessionId = seedSession(courseId, Instant.now().plusSeconds(86400), 20, "SCHEDULED");

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/sessions/" + sessionId, HttpMethod.PATCH,
            authenticated(studentId, Role.STUDENT, Map.of("capacity", 15)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void instructor_creates_a_session_for_their_own_course() {
        UUID instructorId = issueUser(Role.INSTRUCTOR);
        UUID courseId = seedCourse(instructorId);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/courses/" + courseId + "/sessions", HttpMethod.POST,
            authenticated(instructorId, Role.INSTRUCTOR, Map.of(
                "date", "2026-09-16", "startTime", "19:00:00"
            )), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("capacity")).isEqualTo(20);
    }

    @Test
    void instructor_creating_a_session_for_a_course_they_do_not_own_is_rejected() {
        UUID ownerId = UUID.randomUUID();
        UUID courseId = seedCourse(ownerId);
        UUID otherInstructorId = issueUser(Role.INSTRUCTOR);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/courses/" + courseId + "/sessions", HttpMethod.POST,
            authenticated(otherInstructorId, Role.INSTRUCTOR, Map.of(
                "date", "2026-09-16", "startTime", "19:00:00"
            )), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_OWNED");
    }

    @Test
    void batch_creation_generates_one_session_per_matching_day_of_week() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID courseId = seedCourse(UUID.randomUUID());

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/courses/" + courseId + "/sessions/batch", HttpMethod.POST,
            authenticated(adminId, Role.ADMIN, Map.of("fromDate", "2026-09-01", "toDate", "2026-09-30")),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        List<?> sessions = (List<?>) response.getBody().get("sessions");
        assertThat(sessions).hasSize(5);
    }

    @Test
    void management_listing_includes_cancelled_sessions() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID courseId = seedCourse(UUID.randomUUID());
        seedSession(courseId, Instant.now().plusSeconds(3600), 20, "SCHEDULED");
        seedSession(courseId, Instant.now().plusSeconds(7200), 20, "CANCELLED");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(adminId, Role.ADMIN));
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/courses/" + courseId + "/sessions", HttpMethod.GET,
            new HttpEntity<>(headers), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> sessions = (List<?>) response.getBody().get("sessions");
        assertThat(sessions).hasSize(2);
    }

    @Test
    void reducing_capacity_below_assigned_spots_is_rejected() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID sessionId = seedSession(courseId, Instant.now().plusSeconds(86400), 20, "SCHEDULED");
        // Two assignments so a @Positive-legal capacity (>= 1) can still be
        // below assignedSpots -- capacity itself can never be 0 or negative,
        // so the domain rule needs at least 2 assigned spots to be testable
        // through the HTTP layer's bean validation.
        assignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), sessionId, UUID.randomUUID(), Instant.now()
        ));
        assignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), sessionId, UUID.randomUUID(), Instant.now()
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenFor(adminId, Role.ADMIN));
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/sessions/" + sessionId, HttpMethod.PATCH,
            new HttpEntity<>(Map.of("capacity", 1), headers), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("CAPACITY_BELOW_ASSIGNED");
    }

    @Test
    void modifying_a_session_that_already_occurred_is_rejected() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID sessionId = seedSession(courseId, Instant.now().minusSeconds(86400), 20, "SCHEDULED");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenFor(adminId, Role.ADMIN));
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/sessions/" + sessionId, HttpMethod.PATCH,
            new HttpEntity<>(Map.of("notes", "tarde"), headers), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("SESSION_ALREADY_OCCURRED");
    }

    @Test
    void cancelling_a_future_session_succeeds() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID sessionId = seedSession(courseId, Instant.now().plusSeconds(86400), 20, "SCHEDULED");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenFor(adminId, Role.ADMIN));
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/sessions/" + sessionId, HttpMethod.PATCH,
            new HttpEntity<>(Map.of("status", "CANCELLED"), headers), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void payment_verification_driven_assign_honors_capacity_invariant_for_one_payment() {
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID sessionId = seedSession(courseId, Instant.now(), 1, "SCHEDULED");
        UUID paymentId = seedPendingPhysicalPayment("mp-capacity-happy", studentId, sessionId);
        when(paymentProviderPort.fetchPayment("mp-capacity-happy")).thenReturn(
            new ProviderPaymentResult(
                "approved", Money.of(new BigDecimal("100.00"), "ARS"),
                "ext-mp-capacity-happy", "merchant-1"
            )
        );

        verifyAndDispatch("mp-capacity-happy");

        assertThat(assignmentRepository.countBySessionId(sessionId)).isEqualTo(1);
        assertThat(purchaseRepository.findByPaymentId(paymentId).orElseThrow().getStatus())
            .isEqualTo("PENDING_FULFILLMENT");
        ResponseEntity<Map> qrResponse = issueAccessQr(sessionId, studentId);
        assertThat(qrResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(qrResponse.getBody().get("qrCredentials")).isNotNull();
    }

    @Test
    void concurrent_payments_for_same_session_capacity_one_resolves_one_is_exception() throws Exception {
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID sessionId = seedSession(courseId, Instant.now(), 1, "SCHEDULED");
        UUID firstPaymentId = seedPendingPhysicalPayment(
            "mp-concurrent-1", UUID.randomUUID(), sessionId
        );
        UUID secondPaymentId = seedPendingPhysicalPayment(
            "mp-concurrent-2", UUID.randomUUID(), sessionId
        );
        when(paymentProviderPort.fetchPayment("mp-concurrent-1")).thenReturn(
            new ProviderPaymentResult(
                "approved", Money.of(new BigDecimal("100.00"), "ARS"),
                "ext-mp-concurrent-1", "merchant-1"
            )
        );
        when(paymentProviderPort.fetchPayment("mp-concurrent-2")).thenReturn(
            new ProviderPaymentResult(
                "approved", Money.of(new BigDecimal("100.00"), "ARS"),
                "ext-mp-concurrent-2", "merchant-1"
            )
        );
        WebhookInboxJpaEntity firstInbox = inboxRepository.save(new WebhookInboxJpaEntity(
            "mp-concurrent-1:req-1", "mp-concurrent-1", "req-1",
            com.menta.billing.infrastructure.webhook.WebhookInboxStatus.RECEIVED,
            0, null, null, Instant.now(), null
        ));
        WebhookInboxJpaEntity secondInbox = inboxRepository.save(new WebhookInboxJpaEntity(
            "mp-concurrent-2:req-1", "mp-concurrent-2", "req-1",
            com.menta.billing.infrastructure.webhook.WebhookInboxStatus.RECEIVED,
            0, null, null, Instant.now(), null
        ));
        webhookWorker.process(firstInbox);
        webhookWorker.process(secondInbox);
        List<OutboxRowJpaEntity> events = outboxRepository.findAll();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(
                () -> assertThat(outboxWorker.process(events.get(0))).isFalse()
            );
            var second = executor.submit(
                () -> assertThat(outboxWorker.process(events.get(1))).isFalse()
            );
            first.get();
            second.get();
        }

        assertThat(assignmentRepository.countBySessionId(sessionId)).isEqualTo(1);
        assertThat(purchaseRepository.findByPaymentId(firstPaymentId).orElseThrow().getStatus())
            .isIn("PENDING_FULFILLMENT", "EXCEPTION");
        assertThat(purchaseRepository.findByPaymentId(secondPaymentId).orElseThrow().getStatus())
            .isIn("PENDING_FULFILLMENT", "EXCEPTION");
        assertThat(purchaseRepository.findAll()).extracting(purchase -> purchase.getStatus())
            .containsExactlyInAnyOrder("PENDING_FULFILLMENT", "EXCEPTION");
    }

}
