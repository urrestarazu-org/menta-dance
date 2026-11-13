package com.menta.app.integration.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import com.menta.auth.domain.model.UserProfile;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.AttendanceJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityHoldJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.AttendanceJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityHoldJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import com.menta.shared.auth.UserDisplayNamePort;
import com.menta.shared.domain.vo.Email;
import com.menta.shared.physical.AssignmentType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * HTTP-level coverage for {@code GET /api/v1/admin/physical/sessions/{sessionId}/roster} (#355) through
 * the real {@code SecurityConfig} filter chain, the real use case, the real user-name port toward
 * {@code auth} and real MySQL. Every instant is a fixed UTC value (check-ins and sessions), so the suite
 * cannot age out; only the holds are relative to the real clock, because they expire against it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class PhysicalSessionRosterIntegrationTest extends AbstractPhysicalMySqlIntegrationTest {

    private static final String MERCHANT_ACCOUNT_ID = "merchant-integration-session-roster";
    private static final String SESSIONS_URL = "/api/v1/admin/physical/sessions/";
    private static final String SESSION_AT = "2026-10-10T22:00:00Z";
    private static final String PHONE = "+5491100000000";

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
    @Autowired private AttendanceJpaRepository attendanceRepository;

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

    @MockitoSpyBean private UserDisplayNamePort userDisplayNamePort;

    @SuppressWarnings("rawtypes")
    @MockitoBean
    private RedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        attendanceRepository.deleteAll();
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
        when(tokenBlacklistPort.currentTokenVersion(anyString())).thenReturn(OptionalLong.empty());
        return user.getId().getValue();
    }

    private String tokenFor(UUID userId, Role role) {
        User user = new User(
            UserId.of(userId), Email.of("token@example.com"), "hash", role, UserStatus.ACTIVE,
            LocalDateTime.now(), LocalDateTime.now()
        );
        return accessTokenIssuer.issue(user).token();
    }

    /** A student account with a profile, so its display name is "{@code firstName lastName}". */
    private UUID seedStudent(String firstName, String lastName) {
        User student = User.register(
            Email.of(firstName.toLowerCase() + "." + UUID.randomUUID() + "@example.com"), "irrelevant-hash",
            Role.STUDENT, new UserProfile(firstName, lastName, PHONE, null)
        );
        userRepository.save(student);
        return student.getId().getValue();
    }

    /** A student account without a profile (like the ones created before profiles existed). */
    private UUID seedStudentWithoutProfile(String email) {
        User student = User.create(Email.of(email), "irrelevant-hash", Role.STUDENT);
        userRepository.save(student);
        return student.getId().getValue();
    }

    private UUID seedCourse(String title) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new PhysicalCourseJpaEntity(
            id, title, "desc", UUID.randomUUID(), "Ana Perez", "SATURDAY",
            LocalTime.of(22, 0), 60, "INTERMEDIATE", 20, CourseStatus.ACTIVE, now, now
        ));
        return id;
    }

    private UUID seedSession(UUID courseId, int capacity, String status) {
        UUID id = UUID.randomUUID();
        sessionRepository.save(
            new PhysicalSessionJpaEntity(id, courseId, Instant.parse(SESSION_AT), capacity, status, null)
        );
        return id;
    }

    private void seedAssignment(UUID sessionId, UUID studentId, AssignmentType type) {
        assignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), sessionId, studentId, type, Instant.now()
        ));
    }

    private void seedHold(UUID sessionId, Instant expiresAt, Instant convertedAt) {
        holdRepository.save(new PhysicalCapacityHoldJpaEntity(
            UUID.randomUUID(), sessionId, UUID.randomUUID(), expiresAt, convertedAt, Instant.now()
        ));
    }

    private void seedCheckIn(UUID sessionId, UUID studentId, String recordedAt, String kind) {
        attendanceRepository.save(new AttendanceJpaEntity(
            UUID.randomUUID(), sessionId, studentId, Instant.parse(recordedAt), "reader-1", kind
        ));
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> getAs(Role role, String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(issueUser(role), role));
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> rosterAs(Role role, UUID sessionId) {
        return getAs(role, SESSIONS_URL + sessionId + "/roster");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Map<String, Object>> studentsOf(ResponseEntity<Map> response) {
        return (List<Map<String, Object>>) response.getBody().get("students");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map<String, Object> sessionOf(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("session");
    }

    @Test
    void lists_only_the_confirmed_assignments_not_the_holds_of_pending_payments() {
        UUID course = seedCourse("Salsa Intermedio");
        UUID session = seedSession(course, 20, "SCHEDULED");
        UUID monthly = seedStudent("Ana", "Perez");
        UUID individual = seedStudent("Bruno", "Diaz");
        seedAssignment(session, monthly, AssignmentType.MONTHLY);
        seedAssignment(session, individual, AssignmentType.INDIVIDUAL);
        // A student whose payment is still pending only holds a spot while the hold is live (the hold has no
        // student, it is not enrolled yet), and so does one whose hold already expired: neither is listed.
        Instant now = Instant.now();
        seedHold(session, now.plus(30, ChronoUnit.MINUTES), null);
        seedHold(session, now.minus(30, ChronoUnit.MINUTES), null);

        ResponseEntity<Map> response = rosterAs(Role.ADMIN, session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(studentsOf(response)).extracting(item -> item.get("studentId"))
            .containsExactly(monthly.toString(), individual.toString());
        assertThat(studentsOf(response)).extracting(item -> item.get("assignmentType"))
            .containsExactly("MONTHLY", "INDIVIDUAL");
    }

    @Test
    void carries_the_session_header_with_the_roster_size() {
        UUID course = seedCourse("Salsa Intermedio");
        UUID session = seedSession(course, 20, "SCHEDULED");
        seedAssignment(session, seedStudent("Ana", "Perez"), AssignmentType.MONTHLY);
        seedAssignment(session, seedStudent("Bruno", "Diaz"), AssignmentType.INDIVIDUAL);
        // The live hold takes a spot, but it is not a student of the roster.
        seedHold(session, Instant.now().plus(30, ChronoUnit.MINUTES), null);

        Map<String, Object> header = sessionOf(getAs(Role.INSTRUCTOR, SESSIONS_URL + session + "/roster"));

        assertThat(header.get("sessionId")).isEqualTo(session.toString());
        assertThat(header.get("courseId")).isEqualTo(course.toString());
        assertThat(header.get("courseName")).isEqualTo("Salsa Intermedio");
        assertThat(header.get("scheduledAt")).isEqualTo(SESSION_AT);
        assertThat(header.get("status")).isEqualTo("SCHEDULED");
        assertThat(header.get("capacity")).isEqualTo(20);
        assertThat(header.get("studentCount")).isEqualTo(2);
    }

    @Test
    void a_student_with_a_check_in_is_present_with_its_utc_time_and_variant_and_the_rest_are_pending() {
        UUID session = seedSession(seedCourse("Salsa"), 20, "SCHEDULED");
        UUID qr = seedStudent("Ana", "Perez");
        UUID manual = seedStudent("Bruno", "Diaz");
        UUID absent = seedStudent("Carla", "Gomez");
        seedAssignment(session, qr, AssignmentType.MONTHLY);
        seedAssignment(session, manual, AssignmentType.INDIVIDUAL);
        seedAssignment(session, absent, AssignmentType.MONTHLY);
        seedCheckIn(session, qr, "2026-10-10T21:47:12Z", "QR");
        seedCheckIn(session, manual, "2026-10-10T22:05:30Z", "MANUAL");
        // A check-in of another session never makes this student present here.
        UUID otherSession = seedSession(seedCourse("Bachata"), 20, "SCHEDULED");
        seedAssignment(otherSession, absent, AssignmentType.MONTHLY);
        seedCheckIn(otherSession, absent, "2026-10-10T22:01:00Z", "QR");

        List<Map<String, Object>> students = studentsOf(getAs(Role.RECEPTIONIST, SESSIONS_URL + session + "/roster"));

        assertThat(students).hasSize(3);
        Map<String, Object> present = students.get(0);
        assertThat(present.get("displayName")).isEqualTo("Ana Perez");
        assertThat(present.get("attendanceStatus")).isEqualTo("PRESENT");
        assertThat(present.get("checkedInAt")).isEqualTo("2026-10-10T21:47:12Z");
        assertThat(present.get("checkInKind")).isEqualTo("QR");
        Map<String, Object> manuallyPresent = students.get(1);
        assertThat(manuallyPresent.get("displayName")).isEqualTo("Bruno Diaz");
        assertThat(manuallyPresent.get("attendanceStatus")).isEqualTo("PRESENT");
        assertThat(manuallyPresent.get("checkedInAt")).isEqualTo("2026-10-10T22:05:30Z");
        assertThat(manuallyPresent.get("checkInKind")).isEqualTo("MANUAL");
        Map<String, Object> pending = students.get(2);
        assertThat(pending.get("displayName")).isEqualTo("Carla Gomez");
        assertThat(pending.get("attendanceStatus")).isEqualTo("PENDING");
        assertThat(pending).containsEntry("checkedInAt", null).containsEntry("checkInKind", null);
    }

    @Test
    void names_come_from_the_profile_then_the_email_and_an_unknown_user_gets_a_neutral_text() {
        UUID session = seedSession(seedCourse("Salsa"), 20, "SCHEDULED");
        UUID unknown = UUID.fromString("8a1f3c52-0000-4000-8000-000000000001");
        seedAssignment(session, seedStudent("Ana", "Perez"), AssignmentType.MONTHLY);
        seedAssignment(session, seedStudentWithoutProfile("sin.perfil@example.com"), AssignmentType.MONTHLY);
        seedAssignment(session, unknown, AssignmentType.INDIVIDUAL);

        ResponseEntity<Map> response = getAs(Role.ADMIN, SESSIONS_URL + session + "/roster");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(studentsOf(response)).extracting(item -> item.get("displayName"))
            .containsExactly("Alumno 8a1f3c52", "Ana Perez", "sin.perfil@example.com");
        assertThat(studentsOf(response)).extracting(item -> item.get("studentId"))
            .contains(unknown.toString());
    }

    @Test
    void the_names_of_the_whole_roster_are_resolved_in_one_single_call_to_the_port() {
        UUID session = seedSession(seedCourse("Salsa"), 20, "SCHEDULED");
        UUID ana = seedStudent("Ana", "Perez");
        UUID bruno = seedStudent("Bruno", "Diaz");
        UUID unknown = UUID.randomUUID();
        seedAssignment(session, ana, AssignmentType.MONTHLY);
        seedAssignment(session, bruno, AssignmentType.INDIVIDUAL);
        seedAssignment(session, unknown, AssignmentType.MONTHLY);

        ResponseEntity<Map> response = rosterAs(Role.ADMIN, session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> requested = ArgumentCaptor.forClass(Collection.class);
        verify(userDisplayNamePort, times(1)).displayNamesOf(requested.capture());
        assertThat(requested.getValue()).containsExactlyInAnyOrder(ana, bruno, unknown);
    }

    @Test
    void students_are_ordered_by_display_name_and_then_by_id() {
        UUID session = seedSession(seedCourse("Salsa"), 20, "SCHEDULED");
        UUID zoe = seedStudent("Zoe", "Alvarez");
        UUID beto = seedStudent("beto", "Diaz");
        UUID ana = seedStudent("Ana", "Perez");
        seedAssignment(session, zoe, AssignmentType.MONTHLY);
        seedAssignment(session, beto, AssignmentType.MONTHLY);
        seedAssignment(session, ana, AssignmentType.MONTHLY);

        List<Map<String, Object>> students = studentsOf(getAs(Role.ADMIN, SESSIONS_URL + session + "/roster"));

        assertThat(students).extracting(item -> item.get("studentId"))
            .containsExactly(ana.toString(), beto.toString(), zoe.toString());
    }

    @Test
    void a_session_without_students_has_an_empty_roster() {
        UUID session = seedSession(seedCourse("Salsa"), 20, "SCHEDULED");

        ResponseEntity<Map> response = getAs(Role.ADMIN, SESSIONS_URL + session + "/roster");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(studentsOf(response)).isEmpty();
        assertThat(sessionOf(response).get("studentCount")).isEqualTo(0);
    }

    @Test
    void a_cancelled_session_still_shows_its_roster_with_its_status() {
        UUID session = seedSession(seedCourse("Salsa"), 20, "CANCELLED");
        seedAssignment(session, seedStudent("Ana", "Perez"), AssignmentType.MONTHLY);

        ResponseEntity<Map> response = getAs(Role.ADMIN, SESSIONS_URL + session + "/roster");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessionOf(response).get("status")).isEqualTo("CANCELLED");
        assertThat(studentsOf(response)).hasSize(1);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void an_unknown_session_is_a_404_problem() {
        ResponseEntity<Map> response = getAs(Role.ADMIN, SESSIONS_URL + UUID.randomUUID() + "/roster");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(response.getBody().get("code")).isEqualTo("SESSION_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-es-un-uuid", "123", "00000000-0000-0000-0000"})
    @SuppressWarnings("rawtypes")
    void a_malformed_session_id_is_a_400_problem(String sessionId) {
        ResponseEntity<Map> response = getAs(Role.ADMIN, SESSIONS_URL + sessionId + "/roster");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_REQUEST");
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "INSTRUCTOR", "RECEPTIONIST"})
    void admin_instructor_and_receptionist_may_read_the_roster(Role role) {
        UUID session = seedSession(seedCourse("Salsa"), 20, "SCHEDULED");

        assertThat(rosterAs(role, session).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void a_student_is_forbidden() {
        UUID session = seedSession(seedCourse("Salsa"), 20, "SCHEDULED");

        assertThat(rosterAs(Role.STUDENT, session).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void a_request_without_a_token_is_unauthorized() {
        UUID session = seedSession(seedCourse("Salsa"), 20, "SCHEDULED");

        ResponseEntity<Map> response = http.getForEntity(SESSIONS_URL + session + "/roster", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** Reception reads the roster, but the rest of {@code /admin/physical/sessions/**} did not open to it. */
    @Test
    @SuppressWarnings("rawtypes")
    void a_receptionist_still_cannot_patch_a_session() {
        UUID receptionist = issueUser(Role.RECEPTIONIST);
        UUID session = seedSession(seedCourse("Salsa"), 20, "SCHEDULED");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(receptionist, Role.RECEPTIONIST));

        ResponseEntity<Map> response = http.exchange(
            SESSIONS_URL + session, HttpMethod.PATCH, new HttpEntity<>(Map.of("capacity", 5), headers), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(sessionRepository.findById(session).orElseThrow().getCapacity()).isEqualTo(20);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void a_receptionist_cannot_read_a_single_session_nor_post_to_the_roster_path() {
        UUID receptionist = issueUser(Role.RECEPTIONIST);
        UUID session = seedSession(seedCourse("Salsa"), 20, "SCHEDULED");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(receptionist, Role.RECEPTIONIST));

        ResponseEntity<Map> single = http.exchange(
            SESSIONS_URL + session, HttpMethod.GET, new HttpEntity<>(headers), Map.class
        );
        ResponseEntity<Map> post = http.exchange(
            SESSIONS_URL + session + "/roster", HttpMethod.POST, new HttpEntity<>(Map.of(), headers), Map.class
        );

        assertThat(single.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
