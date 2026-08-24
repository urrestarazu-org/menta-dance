package com.menta.app.integration.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.AttendanceJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.AttendanceJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import com.menta.physical.infrastructure.qr.FormatQrCredentialSignatureService;
import com.menta.shared.domain.vo.Email;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
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
 * HTTP-level coverage for US-PHYSICAL-001's check-in flow, through the real
 * {@code SecurityConfig} filter chain — this is what proves {@code
 * access-qr} requires authentication while {@code check-ins} is
 * {@code permitAll()} at the filter level (the door reader authenticates
 * with {@code deviceToken} inside the use case instead).
 *
 * <p>Covers issue #38 escenarios 1, 2, 3, 4 and 5, per the plan's explicit
 * MVP scope. Escenario 6 (Redis compare-and-delete compensation when the
 * second lock or the INSERT fails after the first lock is already held) is
 * OUT OF SCOPE for this PR by explicit user decision — the {@code
 * RedisLockPort} has no compensation, the first lock simply expires by TTL.
 * Simulating that race deterministically would require controlling Redis
 * timing mid-request, which this MySQL+mocked-Redis harness cannot do
 * without inventing fictitious coverage; it is intentionally not attempted
 * here.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class PhysicalCheckInIntegrationTest {

    private static final String DEVICE_TOKEN = "integration-test-device-token";
    private static final FormatQrCredentialSignatureService SIGNATURE_SERVICE =
        new FormatQrCredentialSignatureService();

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
        // Mirrors PaymentWebhookIntegrationTest's HMAC_SECRET override: a
        // known value for the shared door-reader secret, never touching
        // application.yml / application-test.yml.
        registry.add("app.physical.checkin.device-token", () -> DEVICE_TOKEN);
    }

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private PhysicalCourseJpaRepository courseRepository;
    @Autowired private PhysicalSessionJpaRepository sessionRepository;
    @Autowired private PhysicalCapacityAssignmentJpaRepository assignmentRepository;
    @Autowired private AttendanceJpaRepository attendanceRepository;

    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;

    @SuppressWarnings("rawtypes")
    @MockBean
    private RedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
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
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new PhysicalCourseJpaEntity(
            id, "Salsa inicial", "desc", UUID.randomUUID(), "María García", "WEDNESDAY",
            LocalTime.of(20, 0), 60, "INTERMEDIATE", 20, CourseStatus.ACTIVE, now, now
        ));
        return id;
    }

    private UUID seedSession(UUID courseId, Instant scheduledAt) {
        UUID id = UUID.randomUUID();
        sessionRepository.save(
            new PhysicalSessionJpaEntity(id, courseId, scheduledAt, 20, "SCHEDULED", null)
        );
        return id;
    }

    private UUID seedCancelledSession(UUID courseId, Instant scheduledAt) {
        UUID id = UUID.randomUUID();
        sessionRepository.save(
            new PhysicalSessionJpaEntity(id, courseId, scheduledAt, 20, "CANCELLED", null)
        );
        return id;
    }

    private void seedAssignment(UUID sessionId, UUID studentId) {
        assignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), sessionId, studentId, Instant.now()
        ));
    }

    /** Stubs a successful Redis lock acquisition for happy-path scenarios. */
    @SuppressWarnings("unchecked")
    private void stubRedisLockAcquired() {
        ValueOperations valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any())).thenReturn(true);
    }

    private ResponseEntity<Map> issueAccessQr(UUID sessionId, UUID studentId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(studentId, Role.STUDENT));
        return http.exchange(
            "/api/v1/physical/sessions/" + sessionId + "/access-qr", HttpMethod.POST,
            new HttpEntity<>(headers), Map.class
        );
    }

    private ResponseEntity<Map> checkIn(UUID sessionId, String qrCredentials, String deviceToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
            "type", "QR", "qrCredentials", qrCredentials,
            "deviceId", "reader-1", "deviceToken", deviceToken
        );
        return http.exchange(
            "/api/v1/physical/sessions/" + sessionId + "/check-ins", HttpMethod.POST,
            new HttpEntity<>(body, headers), Map.class
        );
    }

    // --- Escenarios 1 + 2: happy path ---------------------------------

    @Test
    void an_assigned_student_issues_a_qr_and_the_door_reader_records_the_first_check_in() {
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        UUID sessionId = seedSession(courseId, Instant.now());
        seedAssignment(sessionId, studentId);

        ResponseEntity<Map> qrResponse = issueAccessQr(sessionId, studentId);
        assertThat(qrResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String qrCredentials = (String) qrResponse.getBody().get("qrCredentials");
        assertThat(qrCredentials).isNotBlank();

        stubRedisLockAcquired();
        ResponseEntity<Map> checkInResponse = checkIn(sessionId, qrCredentials, DEVICE_TOKEN);

        assertThat(checkInResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(attendanceRepository.count()).isEqualTo(1);
    }

    // --- Escenario 3: no confirmed assignment -------------------------

    @Test
    void issuing_a_qr_without_a_confirmed_assignment_is_rejected() {
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        UUID sessionId = seedSession(courseId, Instant.now());

        ResponseEntity<Map> response = issueAccessQr(sessionId, studentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("CAPACITY_ASSIGNMENT_REQUIRED");
    }

    @Test
    void checking_in_without_a_confirmed_assignment_is_rejected() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = seedCourse();
        UUID sessionId = seedSession(courseId, Instant.now());
        String qrCredentials = SIGNATURE_SERVICE.sign(
            studentId.toString(), sessionId.toString(), "jti-1",
            Instant.now().plusSeconds(60).getEpochSecond()
        );

        ResponseEntity<Map> response = checkIn(sessionId, qrCredentials, DEVICE_TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("CAPACITY_ASSIGNMENT_REQUIRED");
    }

    // --- Escenario 4: rejections resolved entirely before Redis -------

    @Test
    void a_qr_credential_bound_to_a_different_session_is_rejected_before_touching_redis() {
        UUID studentId = UUID.randomUUID();
        UUID embeddedSessionId = UUID.randomUUID();
        UUID pathSessionId = UUID.randomUUID();
        String qrCredentials = SIGNATURE_SERVICE.sign(
            studentId.toString(), embeddedSessionId.toString(), "jti-1",
            Instant.now().plusSeconds(60).getEpochSecond()
        );

        ResponseEntity<Map> response = checkIn(pathSessionId, qrCredentials, DEVICE_TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_QR_CREDENTIAL");
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void an_expired_but_well_formed_qr_credential_is_rejected_before_touching_redis() {
        UUID studentId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String qrCredentials = SIGNATURE_SERVICE.sign(
            studentId.toString(), sessionId.toString(), "jti-1",
            Instant.now().minusSeconds(60).getEpochSecond()
        );

        ResponseEntity<Map> response = checkIn(sessionId, qrCredentials, DEVICE_TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody().get("code")).isEqualTo("EXPIRED_QR_CREDENTIAL");
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void a_check_in_outside_the_session_window_is_rejected_before_touching_redis() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = seedCourse();
        // Default app.qr.* window is [-30m, +2h] around scheduledAt; ten
        // days out is comfortably outside it in both directions.
        UUID sessionId = seedSession(courseId, Instant.now().plusSeconds(10 * 24 * 3600));
        String qrCredentials = SIGNATURE_SERVICE.sign(
            studentId.toString(), sessionId.toString(), "jti-1",
            Instant.now().plusSeconds(60).getEpochSecond()
        );

        ResponseEntity<Map> response = checkIn(sessionId, qrCredentials, DEVICE_TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("OUTSIDE_CHECK_IN_WINDOW");
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void an_invalid_device_token_is_rejected_before_touching_redis() {
        ResponseEntity<Map> response = checkIn(UUID.randomUUID(), "qr:irrelevant", "wrong-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_DEVICE_TOKEN");
        verifyNoInteractions(redisTemplate);
    }

    // --- Session-status gap surfaced by review: CANCELLED sessions -----
    // (docs/diagrams/STATE-DIAGRAMS.md: "CANCELLED bloquea check-ins aunque
    // existan asignaciones" — a cancelled session can still carry a
    // confirmed assignment on record, so both endpoints must reject it
    // explicitly rather than relying on the assignment lookup.)

    @Test
    void issuing_a_qr_for_a_cancelled_session_is_rejected() {
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        UUID sessionId = seedCancelledSession(courseId, Instant.now());
        seedAssignment(sessionId, studentId);

        ResponseEntity<Map> response = issueAccessQr(sessionId, studentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("SESSION_CANCELLED");
    }

    @Test
    void checking_in_to_a_cancelled_session_is_rejected_before_touching_redis() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = seedCourse();
        UUID sessionId = seedCancelledSession(courseId, Instant.now());
        String qrCredentials = SIGNATURE_SERVICE.sign(
            studentId.toString(), sessionId.toString(), "jti-1",
            Instant.now().plusSeconds(60).getEpochSecond()
        );

        ResponseEntity<Map> response = checkIn(sessionId, qrCredentials, DEVICE_TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("SESSION_CANCELLED");
        verifyNoInteractions(redisTemplate);
    }

    // --- Escenario 1 gap surfaced by review: issuance must also respect
    // the check-in window, not only capacity assignment -----------------

    @Test
    void issuing_a_qr_outside_the_check_in_window_is_rejected() {
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        UUID sessionId = seedSession(courseId, Instant.now().plusSeconds(10 * 24 * 3600));
        seedAssignment(sessionId, studentId);

        ResponseEntity<Map> response = issueAccessQr(sessionId, studentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("OUTSIDE_CHECK_IN_WINDOW");
    }

    // --- Escenario 5: idempotent replay -------------------------------

    @Test
    void replaying_the_same_check_in_returns_200_on_the_second_scan_and_never_duplicates_the_row() {
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        UUID sessionId = seedSession(courseId, Instant.now());
        seedAssignment(sessionId, studentId);
        ResponseEntity<Map> qrResponse = issueAccessQr(sessionId, studentId);
        String qrCredentials = (String) qrResponse.getBody().get("qrCredentials");

        stubRedisLockAcquired();
        ResponseEntity<Map> first = checkIn(sessionId, qrCredentials, DEVICE_TOKEN);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // The idempotent replay short-circuits on the read-through check
        // (step 8) before ever reaching the locks again — no new Redis
        // stubbing needed for this second call to succeed.
        ResponseEntity<Map> replay = checkIn(sessionId, qrCredentials, DEVICE_TOKEN);

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(attendanceRepository.count()).isEqualTo(1);
    }

    // --- Schema-level idempotency guard (spec.md "Schema-Level Idempotency
    // Guard") -------------------------------------------------------------
    // The application-level lock + read-through check are the primary
    // defenses; this proves the UNIQUE (session_id, user_id) constraint in
    // V15__physical_attendances.sql is the real last-resort backstop against
    // a real MySQL instance, not just documented intent.

    @Test
    void the_database_rejects_a_second_attendance_row_for_the_same_session_and_student() {
        UUID studentId = UUID.randomUUID();
        UUID sessionId = seedSession(seedCourse(), Instant.now());
        attendanceRepository.saveAndFlush(new AttendanceJpaEntity(
            UUID.randomUUID(), sessionId, studentId, Instant.now(), "reader-1", "QR"
        ));

        assertThatThrownBy(() -> attendanceRepository.saveAndFlush(new AttendanceJpaEntity(
            UUID.randomUUID(), sessionId, studentId, Instant.now(), "reader-2", "QR"
        ))).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(attendanceRepository.count()).isEqualTo(1);
    }

    // Escenario 6 (Redis compare-and-delete compensation) is intentionally
    // NOT covered here — see class Javadoc.
}
