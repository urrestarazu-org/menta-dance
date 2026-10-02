package com.menta.app.integration.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.menta.app.integration.support.AbstractPhysicalMySqlIntegrationTest;
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
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.device.Sha256DeviceSecretHasher;
import com.menta.physical.infrastructure.persistence.entity.AttendanceJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalDeviceJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.AttendanceJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalDeviceJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import com.menta.physical.infrastructure.qr.FormatQrCredentialSignatureService;
import com.menta.shared.domain.vo.Email;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * #266: QR check-in authenticates the reader against the device registry, over real HTTP with a
 * real seeded device and real secret hashing.
 *
 * <p>Every rejection scenario posts an otherwise fully valid scan (valid credential, open session,
 * confirmed assignment), so the only thing that can stop it is the device gate: no attendance row
 * and no Redis interaction proves the gate rejected it and short-circuited everything after it.
 * Kept apart from {@code PhysicalCheckInIntegrationTest}, which already exceeds the Checkstyle
 * file-length limit.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class PhysicalCheckInDeviceAuthIntegrationTest extends AbstractPhysicalMySqlIntegrationTest {

    /** The former dev-default shared secret; it must never authenticate anything again. */
    private static final String LEGACY_SHARED_SECRET =
        "ZGV2LW9ubHktY2hlY2tpbi1kZXZpY2UtdG9rZW4tbm90LWZvci1wcm9kdWN0aW9uLXVzZQ==";
    private static final String ALARM_MARKER = "alarm=physical_checkin_device_rejected";
    private static final Sha256DeviceSecretHasher HASHER = new Sha256DeviceSecretHasher();
    private static final FormatQrCredentialSignatureService SIGNATURE_SERVICE =
        new FormatQrCredentialSignatureService();

    /** When set, the check-in clock reads this instant; otherwise it reads the system clock. */
    private static final AtomicReference<Instant> PINNED_NOW = new AtomicReference<>();

    @TestConfiguration
    static class PinnableClockConfiguration {

        @Bean
        @Primary
        Clock pinnableCheckInClock() {
            return () -> {
                Instant pinned = PINNED_NOW.get();
                return pinned != null ? pinned : Instant.now();
            };
        }
    }

    @Autowired private TestRestTemplate http;
    @Autowired private MeterRegistry meterRegistry;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private PhysicalCourseJpaRepository courseRepository;
    @Autowired private PhysicalSessionJpaRepository sessionRepository;
    @Autowired private PhysicalCapacityAssignmentJpaRepository assignmentRepository;
    @Autowired private AttendanceJpaRepository attendanceRepository;
    @Autowired private PhysicalDeviceJpaRepository deviceRepository;

    @SpyBean private PhysicalDeviceRepository deviceRegistry;

    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private BankTransferRateLimitPort bankTransferRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;

    @SuppressWarnings("rawtypes")
    @MockBean
    private RedisTemplate redisTemplate;

    private final List<ILoggingEvent> logEvents = new CopyOnWriteArrayList<>();
    private AppenderBase<ILoggingEvent> logCapture;
    private final Map<String, Double> counterBaseline = new HashMap<>();

    private record Reader(UUID id, String secret) {
    }

    private record Scan(UUID sessionId, UUID studentId, String qrCredentials) {
    }

    @BeforeEach
    void captureLogsAndSnapshotCounters() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logCapture = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                logEvents.add(event);
            }
        };
        logCapture.setContext(root.getLoggerContext());
        logCapture.start();
        // logback-spring.xml sets additivity=false on com.menta, so root alone would miss its logs.
        root.addAppender(logCapture);
        ((Logger) LoggerFactory.getLogger("com.menta")).addAppender(logCapture);
        for (String reason : List.of("unknown_or_invalid", "revoked", "expired")) {
            counterBaseline.put(reason, rejectionCounter(reason));
        }
    }

    @AfterEach
    void cleanUp() {
        PINNED_NOW.set(null);
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logCapture);
        ((Logger) LoggerFactory.getLogger("com.menta")).detachAppender(logCapture);
        logCapture.stop();
        deviceRepository.deleteAll();
        attendanceRepository.deleteAll();
        assignmentRepository.deleteAll();
        sessionRepository.deleteAll();
        courseRepository.deleteAll();
    }

    // --- Fixtures --------------------------------------------------------

    private Reader seedReader(String status, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        String secret = "reader-secret-" + UUID.randomUUID();
        Instant now = Instant.now();
        deviceRepository.save(new PhysicalDeviceJpaEntity(
            id, "Door reader", "Main entrance", HASHER.hash(secret), status, expiresAt, now, now
        ));
        return new Reader(id, secret);
    }

    /** A scan that would succeed if the device gate let it through. */
    private Scan seedValidScan() {
        UUID courseId = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new PhysicalCourseJpaEntity(
            courseId, "Salsa inicial", "desc", UUID.randomUUID(), "María García", "WEDNESDAY",
            LocalTime.of(20, 0), 60, "INTERMEDIATE", 20, CourseStatus.ACTIVE, now, now
        ));
        UUID sessionId = UUID.randomUUID();
        sessionRepository.save(
            new PhysicalSessionJpaEntity(sessionId, courseId, now, 20, "SCHEDULED", null)
        );
        UUID studentId = UUID.randomUUID();
        assignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), sessionId, studentId, now
        ));
        String qrCredentials = SIGNATURE_SERVICE.sign(
            studentId.toString(), sessionId.toString(), "jti-" + UUID.randomUUID(),
            now.plusSeconds(60).getEpochSecond()
        );
        return new Scan(sessionId, studentId, qrCredentials);
    }

    @SuppressWarnings("unchecked")
    private void stubRedisLockAcquired() {
        ValueOperations valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(), any(), any())).thenReturn(true);
    }

    private ResponseEntity<Map> post(UUID sessionId, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
            "/api/v1/physical/sessions/" + sessionId + "/check-ins", HttpMethod.POST,
            new HttpEntity<>(body, headers), Map.class
        );
    }

    private ResponseEntity<Map> checkIn(Scan scan, String deviceId, String deviceToken) {
        return post(scan.sessionId(), Map.of(
            "type", "QR", "qrCredentials", scan.qrCredentials(),
            "deviceId", deviceId, "deviceToken", deviceToken
        ));
    }

    private ResponseEntity<Map> checkIn(Scan scan, Reader reader) {
        return checkIn(scan, reader.id().toString(), reader.secret());
    }

    private double rejectionCounter(String reason) {
        return meterRegistry.get("physical.checkin.device.rejected")
            .tag("reason", reason).counter().count();
    }

    private double rejectionsSinceTestStart(String reason) {
        return rejectionCounter(reason) - counterBaseline.get(reason);
    }

    private List<ILoggingEvent> alarmEvents() {
        return logEvents.stream()
            .filter(e -> e.getFormattedMessage().startsWith(ALARM_MARKER))
            .toList();
    }

    private void assertRejectedBeforeAnyLaterGate(ResponseEntity<Map> response, String code) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo(code);
        verifyNoInteractions(redisTemplate);
        assertThat(attendanceRepository.count()).isZero();
    }

    // --- Accepted device -------------------------------------------------

    @Test
    void an_active_device_without_expiry_checks_in_and_attendance_stores_its_uuid() {
        Reader reader = seedReader("ACTIVE", null);
        Scan scan = seedValidScan();
        stubRedisLockAcquired();

        ResponseEntity<Map> response = checkIn(scan, reader);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AttendanceJpaEntity stored = attendanceRepository
            .findBySessionIdAndUserId(scan.sessionId(), scan.studentId()).orElseThrow();
        assertThat(stored.getDeviceId()).isEqualTo(reader.id().toString());
        assertThat(stored.getKind()).isEqualTo("QR");
        assertThat(alarmEvents()).isEmpty();
        assertThat(rejectionsSinceTestStart("unknown_or_invalid")).isZero();
    }

    // --- Indistinguishable INVALID_DEVICE_TOKEN ---------------------------

    @Test
    void unknown_uuid_non_uuid_id_wrong_secret_and_legacy_secret_return_identical_401_bodies() {
        Reader reader = seedReader("ACTIVE", null);
        Scan scan = seedValidScan();

        Map<String, ResponseEntity<Map>> responses = new LinkedHashMap<>();
        responses.put("wrong secret", checkIn(scan, reader.id().toString(), "not-the-secret"));
        responses.put("unknown uuid", checkIn(scan, UUID.randomUUID().toString(), reader.secret()));
        responses.put("non-uuid id", checkIn(scan, "reader-1", reader.secret()));
        responses.put("legacy secret", checkIn(scan, "reader-1", LEGACY_SHARED_SECRET));
        responses.put("legacy secret on a registered id",
            checkIn(scan, reader.id().toString(), LEGACY_SHARED_SECRET));

        Map<?, ?> reference = responses.get("wrong secret").getBody();
        assertThat(reference.get("code")).isEqualTo("INVALID_DEVICE_TOKEN");
        responses.forEach((label, response) -> {
            assertThat(response.getStatusCode()).as(label).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).as(label).isEqualTo(reference);
        });
        verifyNoInteractions(redisTemplate);
        assertThat(attendanceRepository.count()).isZero();
        assertThat(rejectionsSinceTestStart("unknown_or_invalid")).isEqualTo(responses.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"reader-1", "not-a-uuid", "1-1-1-1-1", "0"})
    void a_non_uuid_device_id_is_rejected_with_401_and_never_queries_the_registry(String id) {
        Scan scan = seedValidScan();

        ResponseEntity<Map> response = checkIn(scan, id, "some-secret");

        assertRejectedBeforeAnyLaterGate(response, "INVALID_DEVICE_TOKEN");
        verifyNoInteractions(deviceRegistry);
    }

    @Test
    void an_unknown_uuid_does_query_the_registry() {
        Scan scan = seedValidScan();

        ResponseEntity<Map> response = checkIn(scan, UUID.randomUUID().toString(), "some-secret");

        assertRejectedBeforeAnyLaterGate(response, "INVALID_DEVICE_TOKEN");
        verify(deviceRegistry).findById(any());
    }

    // --- Revoked and expired, only after the secret is proven -------------

    @Test
    void a_revoked_device_with_its_correct_secret_is_rejected_as_revoked() {
        Reader reader = seedReader("REVOKED", null);
        Scan scan = seedValidScan();

        ResponseEntity<Map> response = checkIn(scan, reader);

        assertRejectedBeforeAnyLaterGate(response, "DEVICE_REVOKED");
        assertThat(rejectionsSinceTestStart("revoked")).isEqualTo(1);
    }

    @Test
    void an_expired_device_with_its_correct_secret_is_rejected_as_expired() {
        Reader reader = seedReader("ACTIVE", Instant.now().minus(1, ChronoUnit.DAYS));
        Scan scan = seedValidScan();

        ResponseEntity<Map> response = checkIn(scan, reader);

        assertRejectedBeforeAnyLaterGate(response, "DEVICE_EXPIRED");
        assertThat(rejectionsSinceTestStart("expired")).isEqualTo(1);
    }

    @Test
    void a_device_whose_expiry_equals_the_current_instant_is_expired() {
        Instant pinned = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        PINNED_NOW.set(pinned);
        Reader reader = seedReader("ACTIVE", pinned);
        Scan scan = seedValidScan();

        ResponseEntity<Map> response = checkIn(scan, reader);

        assertRejectedBeforeAnyLaterGate(response, "DEVICE_EXPIRED");
        assertThat(rejectionsSinceTestStart("expired")).isEqualTo(1);
    }

    @Test
    void a_device_with_one_second_left_before_its_expiry_still_checks_in() {
        Instant pinned = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        PINNED_NOW.set(pinned);
        Reader reader = seedReader("ACTIVE", pinned.plusSeconds(1));
        Scan scan = seedValidScan();
        stubRedisLockAcquired();

        ResponseEntity<Map> response = checkIn(scan, reader);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(rejectionsSinceTestStart("expired")).isZero();
    }

    @Test
    void a_revoked_device_that_is_also_past_its_expiry_is_reported_as_revoked() {
        Reader reader = seedReader("REVOKED", Instant.now().minus(1, ChronoUnit.DAYS));
        Scan scan = seedValidScan();

        ResponseEntity<Map> response = checkIn(scan, reader);

        assertRejectedBeforeAnyLaterGate(response, "DEVICE_REVOKED");
        assertThat(rejectionsSinceTestStart("revoked")).isEqualTo(1);
        assertThat(rejectionsSinceTestStart("expired")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"REVOKED", "EXPIRED"})
    void a_revoked_or_expired_device_with_a_wrong_secret_reveals_nothing(String state) {
        Reader reader = "REVOKED".equals(state)
            ? seedReader("REVOKED", null)
            : seedReader("ACTIVE", Instant.now().minus(1, ChronoUnit.DAYS));
        Reader unrelated = seedReader("ACTIVE", null);
        Scan scan = seedValidScan();

        ResponseEntity<Map> response = checkIn(scan, reader.id().toString(), "wrong-secret");
        ResponseEntity<Map> reference = checkIn(scan, unrelated.id().toString(), "wrong-secret");

        assertRejectedBeforeAnyLaterGate(response, "INVALID_DEVICE_TOKEN");
        assertThat(response.getBody()).isEqualTo(reference.getBody());
        assertThat(rejectionsSinceTestStart("revoked")).isZero();
        assertThat(rejectionsSinceTestStart("expired")).isZero();
        assertThat(rejectionsSinceTestStart("unknown_or_invalid")).isEqualTo(2);
    }

    // --- Observability ----------------------------------------------------

    @Test
    void a_revoked_rejection_writes_one_warn_line_and_increments_only_the_revoked_counter() {
        Reader reader = seedReader("REVOKED", null);
        Scan scan = seedValidScan();

        checkIn(scan, reader);

        List<ILoggingEvent> alarms = alarmEvents();
        assertThat(alarms).hasSize(1);
        assertThat(alarms.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(alarms.get(0).getFormattedMessage()).isEqualTo(
            "alarm=physical_checkin_device_rejected reason=revoked deviceId=" + reader.id()
        );
        assertThat(rejectionsSinceTestStart("revoked")).isEqualTo(1);
        assertThat(rejectionsSinceTestStart("expired")).isZero();
        assertThat(rejectionsSinceTestStart("unknown_or_invalid")).isZero();
    }

    @Test
    void a_non_uuid_rejection_logs_no_device_id_and_neither_the_raw_id_nor_the_secret_leak() {
        Scan scan = seedValidScan();
        String secret = "super-secret-" + UUID.randomUUID();

        checkIn(scan, "reader-1", secret);

        List<ILoggingEvent> alarms = alarmEvents();
        assertThat(alarms).hasSize(1);
        assertThat(alarms.get(0).getFormattedMessage()).isEqualTo(
            "alarm=physical_checkin_device_rejected reason=unknown_or_invalid deviceId=none"
        );
        assertThat(rejectionsSinceTestStart("unknown_or_invalid")).isEqualTo(1);
        assertThat(logEvents).isNotEmpty().allSatisfy(event ->
            assertThat(event.getFormattedMessage())
                .doesNotContain("reader-1").doesNotContain(secret)
        );
    }

    @Test
    void a_wrong_secret_on_a_registered_device_logs_its_uuid_but_never_the_secret() {
        Reader reader = seedReader("ACTIVE", null);
        Scan scan = seedValidScan();
        String wrongSecret = "wrong-secret-" + UUID.randomUUID();

        checkIn(scan, reader.id().toString(), wrongSecret);

        List<ILoggingEvent> alarms = alarmEvents();
        assertThat(alarms).hasSize(1);
        assertThat(alarms.get(0).getFormattedMessage()).isEqualTo(
            "alarm=physical_checkin_device_rejected reason=unknown_or_invalid deviceId="
                + reader.id()
        );
        assertThat(logEvents).allSatisfy(event ->
            assertThat(event.getFormattedMessage()).doesNotContain(wrongSecret)
                .doesNotContain(reader.secret())
        );
    }

    // --- Request validation is unchanged ----------------------------------

    @Test
    void blank_or_absent_device_credentials_keep_the_400_invalid_request_rejection() {
        Scan scan = seedValidScan();
        Reader reader = seedReader("ACTIVE", null);
        List<Map<String, Object>> bodies = List.of(
            Map.of("type", "QR", "qrCredentials", scan.qrCredentials(),
                "deviceId", "", "deviceToken", reader.secret()),
            Map.of("type", "QR", "qrCredentials", scan.qrCredentials(),
                "deviceId", reader.id().toString(), "deviceToken", ""),
            Map.of("type", "QR", "qrCredentials", scan.qrCredentials(),
                "deviceToken", reader.secret()),
            Map.of("type", "QR", "qrCredentials", scan.qrCredentials(),
                "deviceId", reader.id().toString())
        );

        for (Map<String, Object> body : bodies) {
            ResponseEntity<Map> response = post(scan.sessionId(), body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("code")).isEqualTo("INVALID_REQUEST");
        }
        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(deviceRegistry);
        assertThat(alarmEvents()).isEmpty();
    }

    // --- MANUAL never authenticates a device -------------------------------

    @Test
    void a_manual_check_in_runs_no_device_authentication() {
        User receptionist = User.create(
            Email.of("receptionist-" + UUID.randomUUID() + "@example.com"), "hash",
            Role.RECEPTIONIST
        );
        userRepository.save(receptionist);
        User student = User.create(
            Email.of("student-" + UUID.randomUUID() + "@example.com"), "hash", Role.STUDENT
        );
        userRepository.save(student);
        when(tokenBlacklistPort.isBlacklisted(any())).thenReturn(false);
        when(tokenBlacklistPort.currentTokenVersion(any())).thenReturn(OptionalLong.empty());
        UUID studentId = student.getId().getValue();
        Scan scan = seedValidScan();
        assignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), scan.sessionId(), studentId, Instant.now()
        ));
        stubRedisLockAcquired();
        UUID receptionistId = receptionist.getId().getValue();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessTokenIssuer.issue(new User(
            UserId.of(receptionistId), Email.of("token@example.com"), "hash", Role.RECEPTIONIST,
            UserStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now()
        )).token());

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/physical/sessions/" + scan.sessionId() + "/check-ins", HttpMethod.POST,
            new HttpEntity<>(Map.of("type", "MANUAL", "studentId", studentId.toString()), headers),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AttendanceJpaEntity stored = attendanceRepository
            .findBySessionIdAndUserId(scan.sessionId(), studentId).orElseThrow();
        assertThat(stored.getDeviceId()).isEqualTo(receptionistId.toString());
        verifyNoInteractions(deviceRegistry);
        assertThat(alarmEvents()).isEmpty();
    }
}
