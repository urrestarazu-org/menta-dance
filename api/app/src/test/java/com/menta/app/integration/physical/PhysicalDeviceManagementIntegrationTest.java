package com.menta.app.integration.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.physical.application.port.out.PhysicalDeviceAuditRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalDeviceAuditJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalDeviceJpaRepository;
import com.menta.shared.domain.vo.Email;
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
 * HTTP-level coverage for the device registry (#44, US-PHYSICAL-007, P3), through the real {@code
 * SecurityConfig} filter chain — this is what proves the new
 * {@code /api/v1/admin/physical/devices/**} matcher and, more importantly, the two properties a
 * unit test cannot exercise end to end: the rollback proof (C5 — an audit-append failure leaves
 * NO device row, proving {@code TransactionalRegisterPhysicalDeviceUseCase} really shares one
 * commit) and the full register → get → rotate → revoke → second-revoke-409 →
 * rotate-after-revoke-409 lifecycle. Mirrors {@code PhysicalCourseManagementIntegrationTest} for
 * the mock set and {@code PhysicalAttendanceHistoryIntegrationTest} for the Testcontainers MySQL
 * setup (real unique-constraint/transaction semantics that {@code create-drop} H2 would not
 * reliably reproduce).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class PhysicalDeviceManagementIntegrationTest {

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
    }

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private PhysicalDeviceJpaRepository deviceRepository;
    @Autowired private PhysicalDeviceAuditJpaRepository auditRepository;

    /**
     * C5 rollback proof: forces the audit adapter to throw, then asserts NO device row survives.
     * This is the one property a Mockito unit test (2.16/2.17) cannot prove — it exercises the
     * real transaction manager, not a mocked {@code @Transactional} boundary.
     */
    @MockBean private PhysicalDeviceAuditRepository physicalDeviceAuditRepository;

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

    @AfterEach
    void cleanUp() {
        auditRepository.deleteAll();
        deviceRepository.deleteAll();
    }

    private UUID issueUser(Role role) {
        User user = User.create(
            Email.of(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com"),
            "irrelevant-hash", role
        );
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

    private HttpEntity<Void> authenticated(UUID userId, Role role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(userId, role));
        return new HttpEntity<>(headers);
    }

    private ResponseEntity<Map> registerDevice(UUID callerId, Role role) {
        return http.exchange(
            "/api/v1/admin/physical/devices", HttpMethod.POST,
            authenticated(callerId, role, Map.of("name", "Puerta principal", "location", "Sede Centro")),
            Map.class
        );
    }

    @Test
    void an_audit_append_failure_leaves_no_device_row_behind() {
        UUID adminId = issueUser(Role.ADMIN);
        doThrow(new RuntimeException("simulated audit-append failure"))
            .when(physicalDeviceAuditRepository)
            .append(any(), any(), anyString(), any(), anyString());

        ResponseEntity<Map> response = registerDevice(adminId, Role.ADMIN);

        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
        assertThat(deviceRepository.findAll()).isEmpty();
    }

    @Test
    void register_get_rotate_revoke_second_revoke_and_rotate_after_revoke_round_trip_through_the_full_stack() {
        UUID adminId = issueUser(Role.ADMIN);

        ResponseEntity<Map> registerResponse = registerDevice(adminId, Role.ADMIN);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map registerBody = registerResponse.getBody();
        String rawSecretAtRegistration = (String) registerBody.get("secret");
        assertThat(rawSecretAtRegistration).isNotBlank();
        Map registeredDevice = (Map) registerBody.get("device");
        String deviceId = (String) registeredDevice.get("id");
        assertThat(registeredDevice.get("status")).isEqualTo("ACTIVE");
        assertThat(registeredDevice).doesNotContainKey("secret");
        assertThat(registeredDevice).doesNotContainKey("secretHash");

        ResponseEntity<Map> getResponse = http.exchange(
            "/api/v1/admin/physical/devices/" + deviceId, HttpMethod.GET,
            authenticated(adminId, Role.ADMIN), Map.class
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).doesNotContainKey("secret");
        assertThat(getResponse.getBody()).doesNotContainKey("secretHash");

        ResponseEntity<Map> rotateResponse = http.exchange(
            "/api/v1/admin/physical/devices/" + deviceId + "/rotate-secret", HttpMethod.POST,
            authenticated(adminId, Role.ADMIN), Map.class
        );
        assertThat(rotateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rotatedSecret = (String) rotateResponse.getBody().get("secret");
        assertThat(rotatedSecret).isNotBlank().isNotEqualTo(rawSecretAtRegistration);

        ResponseEntity<Map> revokeResponse = http.exchange(
            "/api/v1/admin/physical/devices/" + deviceId + "/revoke", HttpMethod.POST,
            authenticated(adminId, Role.ADMIN), Map.class
        );
        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(revokeResponse.getBody().get("status")).isEqualTo("REVOKED");

        ResponseEntity<Map> secondRevokeResponse = http.exchange(
            "/api/v1/admin/physical/devices/" + deviceId + "/revoke", HttpMethod.POST,
            authenticated(adminId, Role.ADMIN), Map.class
        );
        assertThat(secondRevokeResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondRevokeResponse.getBody().get("code")).isEqualTo("DEVICE_ALREADY_REVOKED");

        ResponseEntity<Map> rotateAfterRevokeResponse = http.exchange(
            "/api/v1/admin/physical/devices/" + deviceId + "/rotate-secret", HttpMethod.POST,
            authenticated(adminId, Role.ADMIN), Map.class
        );
        assertThat(rotateAfterRevokeResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rotateAfterRevokeResponse.getBody().get("code")).isEqualTo("DEVICE_REVOKED");

        ResponseEntity<Map> listResponse = http.exchange(
            "/api/v1/admin/physical/devices", HttpMethod.GET, authenticated(adminId, Role.ADMIN), Map.class
        );
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> devices = (List<?>) listResponse.getBody().get("devices");
        assertThat(devices).hasSize(1);
        Map onlyDevice = (Map) devices.get(0);
        assertThat(onlyDevice.get("id")).isEqualTo(deviceId);
        assertThat(onlyDevice.get("status")).isEqualTo("REVOKED");
        assertThat(onlyDevice).doesNotContainKey("secret");
        assertThat(onlyDevice).doesNotContainKey("secretHash");
    }

    @Test
    void student_is_rejected_before_reaching_the_controller() {
        UUID studentId = issueUser(Role.STUDENT);

        ResponseEntity<Map> response = registerDevice(studentId, Role.STUDENT);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void instructor_is_rejected_before_reaching_the_controller() {
        UUID instructorId = issueUser(Role.INSTRUCTOR);

        ResponseEntity<Map> response = registerDevice(instructorId, Role.INSTRUCTOR);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void an_anonymous_request_is_rejected_with_401() {
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/devices", HttpMethod.GET, HttpEntity.EMPTY, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
