package com.menta.app.integration.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityHoldJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityHoldJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import com.menta.shared.domain.vo.Email;
import com.menta.shared.physical.AssignmentType;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * HTTP-level coverage for {@code GET /api/v1/physical/sessions/me} (#356) through the real
 * {@code SecurityConfig} filter chain, the real use case and real MySQL. Session dates are
 * relative to the real clock, so the suite cannot age out.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class PhysicalMySessionsIntegrationTest extends AbstractPhysicalMySqlIntegrationTest {

    private static final String MERCHANT_ACCOUNT_ID = "merchant-integration-attendance-history";
    private static final String URL = "/api/v1/physical/sessions/me";

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
    @Autowired private PhysicalCapacityHoldJpaRepository holdRepository;

    @MockitoBean private AuthDegradedGuard authDegradedGuard;
    @MockitoBean private TokenBlacklistPort tokenBlacklistPort;
    @MockitoBean private LoginRateLimitPort loginRateLimitPort;
    @MockitoBean private ActivationRateLimitPort activationRateLimitPort;
    @MockitoBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockitoBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockitoBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockitoBean private BankTransferRateLimitPort bankTransferRateLimitPort;
    @MockitoBean private CourseCatalogPort courseCatalogPort;
    @MockitoBean private PaymentPreferencePort paymentPreferencePort;
    @MockitoBean private PaymentProviderPort paymentProviderPort;

    @SuppressWarnings("rawtypes")
    @MockitoBean
    private RedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        holdRepository.deleteAll();
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

    private UUID seedCourse(String title) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new PhysicalCourseJpaEntity(
            id, title, "desc", UUID.randomUUID(), "Ana Perez", "WEDNESDAY",
            LocalTime.of(20, 0), 60, "INTERMEDIATE", 20, CourseStatus.ACTIVE, now, now
        ));
        return id;
    }

    private UUID seedSession(UUID courseId, Instant scheduledAt, String status) {
        UUID id = UUID.randomUUID();
        sessionRepository.save(new PhysicalSessionJpaEntity(id, courseId, scheduledAt, 20, status, null));
        return id;
    }

    private void seedAssignment(UUID sessionId, UUID studentId) {
        seedAssignment(sessionId, studentId, AssignmentType.MONTHLY);
    }

    private void seedAssignment(UUID sessionId, UUID studentId, AssignmentType type) {
        assignmentRepository.save(
            new PhysicalCapacityAssignmentJpaEntity(
                UUID.randomUUID(), sessionId, studentId, type, Instant.now()
            )
        );
    }

    private void seedHold(UUID sessionId) {
        Instant now = Instant.now();
        holdRepository.save(new PhysicalCapacityHoldJpaEntity(
            UUID.randomUUID(), sessionId, UUID.randomUUID(), now.plus(30, ChronoUnit.MINUTES), null, now
        ));
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> getAs(UUID userId, Role role, String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(userId, role));
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Map<String, Object>> sessionsOf(ResponseEntity<Map> response) {
        return (List<Map<String, Object>>) response.getBody().get("sessions");
    }

    @Test
    void lists_only_the_confirmed_upcoming_sessions_in_ascending_order() {
        UUID student = issueUser(Role.STUDENT);
        UUID salsa = seedCourse("Salsa Intermedio");
        UUID bachata = seedCourse("Bachata Inicial");
        Instant now = Instant.now();

        // Two confirmed assignments, seeded out of order: a plan session and a one-off session.
        UUID later = seedSession(bachata, now.plus(10, ChronoUnit.DAYS), "SCHEDULED");
        UUID sooner = seedSession(salsa, now.plus(2, ChronoUnit.DAYS), "SCHEDULED");
        seedAssignment(later, student);
        seedAssignment(sooner, student);
        // A technical hold is not an assignment.
        seedHold(seedSession(salsa, now.plus(3, ChronoUnit.DAYS), "SCHEDULED"));
        // Past, beyond the 60-day range, cancelled: assigned but not listed.
        seedAssignment(seedSession(salsa, now.minus(3, ChronoUnit.DAYS), "SCHEDULED"), student);
        seedAssignment(seedSession(salsa, now.plus(90, ChronoUnit.DAYS), "SCHEDULED"), student);
        seedAssignment(seedSession(salsa, now.plus(4, ChronoUnit.DAYS), "CANCELLED"), student);
        // Someone else's assignment.
        UUID other = issueUser(Role.STUDENT);
        seedAssignment(seedSession(salsa, now.plus(5, ChronoUnit.DAYS), "SCHEDULED"), other);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = getAs(student, Role.STUDENT, URL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> sessions = sessionsOf(response);
        assertThat(sessions).extracting(item -> item.get("sessionId"))
            .containsExactly(sooner.toString(), later.toString());
        Map<String, Object> first = sessions.get(0);
        assertThat(first.get("courseId")).isEqualTo(salsa.toString());
        assertThat(first.get("courseName")).isEqualTo("Salsa Intermedio");
        assertThat(first.get("instructorName")).isEqualTo("Ana Perez");
        assertThat(Instant.parse((String) first.get("scheduledAt")))
            .isCloseTo(now.plus(2, ChronoUnit.DAYS), org.assertj.core.api.Assertions.within(1, ChronoUnit.SECONDS));
        assertThat(sessions).extracting(item -> item.get("checkInOpen")).containsExactly(false, false);
        assertThat(Instant.parse((String) response.getBody().get("from"))).isBefore(Instant.now());
        assertThat(Instant.parse((String) response.getBody().get("to"))).isAfter(now.plus(59, ChronoUnit.DAYS));
    }

    @Test
    void a_monthly_session_exposes_assignment_type_monthly() {
        UUID student = issueUser(Role.STUDENT);
        seedAssignment(seedSession(
            seedCourse("Salsa Intermedio"), Instant.now().plus(2, ChronoUnit.DAYS), "SCHEDULED"),
            student, AssignmentType.MONTHLY);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = getAs(student, Role.STUDENT, URL);

        assertThat(sessionsOf(response)).singleElement()
            .satisfies(item -> assertThat(item.get("assignmentType")).isEqualTo("MONTHLY"));
    }

    @Test
    void an_individual_session_exposes_assignment_type_individual() {
        UUID student = issueUser(Role.STUDENT);
        seedAssignment(seedSession(
            seedCourse("Salsa Intermedio"), Instant.now().plus(2, ChronoUnit.DAYS), "SCHEDULED"),
            student, AssignmentType.INDIVIDUAL);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = getAs(student, Role.STUDENT, URL);

        assertThat(sessionsOf(response)).singleElement()
            .satisfies(item -> assertThat(item.get("assignmentType")).isEqualTo("INDIVIDUAL"));
    }

    @Test
    void mixed_types_in_one_response_each_keep_their_own_type() {
        UUID student = issueUser(Role.STUDENT);
        UUID course = seedCourse("Salsa Intermedio");
        Instant now = Instant.now();
        seedAssignment(seedSession(course, now.plus(2, ChronoUnit.DAYS), "SCHEDULED"),
            student, AssignmentType.MONTHLY);
        seedAssignment(seedSession(course, now.plus(3, ChronoUnit.DAYS), "SCHEDULED"),
            student, AssignmentType.INDIVIDUAL);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = getAs(student, Role.STUDENT, URL);

        assertThat(sessionsOf(response)).extracting(item -> item.get("assignmentType"))
            .containsExactly("MONTHLY", "INDIVIDUAL");
    }

    @Test
    void a_session_inside_its_check_in_window_is_flagged_open() {
        UUID student = issueUser(Role.STUDENT);
        UUID inWindow = seedSession(
            seedCourse("Salsa Intermedio"), Instant.now().plus(10, ChronoUnit.MINUTES), "SCHEDULED");
        seedAssignment(inWindow, student);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = getAs(student, Role.STUDENT, URL);

        assertThat(sessionsOf(response)).singleElement()
            .satisfies(item -> assertThat(item.get("checkInOpen")).isEqualTo(true));
    }

    @Test
    void another_student_does_not_see_the_first_students_sessions() {
        UUID student = issueUser(Role.STUDENT);
        UUID other = issueUser(Role.STUDENT);
        UUID session = seedSession(
            seedCourse("Salsa Intermedio"), Instant.now().plus(2, ChronoUnit.DAYS), "SCHEDULED");
        seedAssignment(session, student);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = getAs(other, Role.STUDENT, URL + "?studentId=" + student);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessionsOf(response)).isEmpty();
    }

    @Test
    void without_a_token_the_route_is_unauthorized() {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = http.getForEntity(URL, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void roles_other_than_student_are_forbidden() {
        for (Role role : List.of(Role.ADMIN, Role.INSTRUCTOR)) {
            UUID user = issueUser(role);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = getAs(user, role, URL);

            assertThat(response.getStatusCode()).as(role.name()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
