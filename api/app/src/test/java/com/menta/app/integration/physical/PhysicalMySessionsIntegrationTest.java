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
import java.net.URI;
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
import org.springframework.boot.test.web.server.LocalServerPort;
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
 * HTTP-level coverage for {@code GET /api/v1/physical/sessions/me?from=&to=} (#356, #729) through the
 * real {@code SecurityConfig} filter chain, the real use case and real MySQL. The range is UTC and
 * half-open; the client computes it from its user's local day or month. Session dates are relative to
 * the real clock (or a fixed Buenos Aires day, where the test is about its edges), so the suite cannot
 * age out.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class PhysicalMySessionsIntegrationTest extends AbstractPhysicalMySqlIntegrationTest {

    private static final String MERCHANT_ACCOUNT_ID = "merchant-integration-attendance-history";
    private static final String URL = "/api/v1/physical/sessions/me";
    private static final String RANGE_DETAIL = "The range must have from before to and span at most 92 days.";
    // The local day 2026-11-12 in Buenos Aires (UTC-3), as the client computes it.
    private static final Instant BA_DAY_FROM = Instant.parse("2026-11-12T03:00:00Z");
    private static final Instant BA_DAY_TO = Instant.parse("2026-11-13T03:00:00Z");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("billing.webhook.reconcile-rate-ms", () -> "999999999");
        registry.add("billing.mercadopago.merchant-account-id", () -> MERCHANT_ACCOUNT_ID);
    }

    @LocalServerPort private int port;
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

    /** Sends the exact path and query as given, with no template encoding: {@code %2B} and a bare {@code +}. */
    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> getRaw(UUID userId, Role role, String pathAndQuery) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(userId, role));
        return http.exchange(
            URI.create("http://localhost:" + port + pathAndQuery), HttpMethod.GET, new HttpEntity<>(headers), Map.class
        );
    }

    private static String wideRange() {
        Instant now = Instant.now();
        return range(now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS));
    }

    private static String range(Instant from, Instant to) {
        return URL + "?from=" + from + "&to=" + to;
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
        // Past, beyond the requested range, cancelled: assigned but not listed.
        seedAssignment(seedSession(salsa, now.minus(3, ChronoUnit.DAYS), "SCHEDULED"), student);
        seedAssignment(seedSession(salsa, now.plus(90, ChronoUnit.DAYS), "SCHEDULED"), student);
        seedAssignment(seedSession(salsa, now.plus(4, ChronoUnit.DAYS), "CANCELLED"), student);
        // Someone else's assignment.
        UUID other = issueUser(Role.STUDENT);
        seedAssignment(seedSession(salsa, now.plus(5, ChronoUnit.DAYS), "SCHEDULED"), other);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response =
            getAs(student, Role.STUDENT, range(now.minus(1, ChronoUnit.HOURS), now.plus(60, ChronoUnit.DAYS)));

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
        assertThat(Instant.parse((String) response.getBody().get("from")))
            .isEqualTo(now.minus(1, ChronoUnit.HOURS));
        assertThat(Instant.parse((String) response.getBody().get("to"))).isEqualTo(now.plus(60, ChronoUnit.DAYS));
    }

    @Test
    void a_monthly_session_exposes_assignment_type_monthly() {
        UUID student = issueUser(Role.STUDENT);
        seedAssignment(seedSession(
            seedCourse("Salsa Intermedio"), Instant.now().plus(2, ChronoUnit.DAYS), "SCHEDULED"),
            student, AssignmentType.MONTHLY);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = getAs(student, Role.STUDENT, wideRange());

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
        ResponseEntity<Map> response = getAs(student, Role.STUDENT, wideRange());

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
        ResponseEntity<Map> response = getAs(student, Role.STUDENT, wideRange());

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
        ResponseEntity<Map> response = getAs(student, Role.STUDENT, wideRange());

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
        ResponseEntity<Map> response = getAs(other, Role.STUDENT, wideRange() + "&studentId=" + student);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessionsOf(response)).isEmpty();
    }

    @Test
    void without_a_token_the_route_is_unauthorized() {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = http.getForEntity(wideRange(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void roles_other_than_student_are_forbidden() {
        for (Role role : List.of(Role.ADMIN, Role.INSTRUCTOR)) {
            UUID user = issueUser(role);

            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = getAs(user, role, wideRange());

            assertThat(response.getStatusCode()).as(role.name()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    /**
     * #729 — the local day of Buenos Aires in UTC: a class at 00:30 local (03:30Z) and one at 23:30 local
     * (02:30Z the next UTC day) both belong to it; 23:59:59 of the day before is out, and a class exactly
     * at {@code from} is in while one exactly at {@code to} is out.
     */
    @Test
    void a_buenos_aires_day_keeps_the_00_30_and_23_30_local_classes_and_is_half_open() {
        UUID student = issueUser(Role.STUDENT);
        UUID course = seedCourse("Salsa Intermedio");
        UUID previousNight = seedSession(course, BA_DAY_FROM.minusSeconds(1), "SCHEDULED");
        UUID atFrom = seedSession(course, BA_DAY_FROM, "SCHEDULED");
        UUID justAfterMidnight = seedSession(course, Instant.parse("2026-11-12T03:30:00Z"), "SCHEDULED");
        UUID lateEvening = seedSession(course, Instant.parse("2026-11-13T02:30:00Z"), "SCHEDULED");
        UUID atTo = seedSession(course, BA_DAY_TO, "SCHEDULED");
        for (UUID session : List.of(previousNight, atFrom, justAfterMidnight, lateEvening, atTo)) {
            seedAssignment(session, student);
        }

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = getAs(student, Role.STUDENT, range(BA_DAY_FROM, BA_DAY_TO));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("from")).isEqualTo("2026-11-12T03:00:00Z");
        assertThat(response.getBody().get("to")).isEqualTo("2026-11-13T03:00:00Z");
        assertThat(sessionsOf(response)).extracting(item -> item.get("sessionId"))
            .containsExactly(atFrom.toString(), justAfterMidnight.toString(), lateEvening.toString());
    }

    /** #729 — the local month of November in Buenos Aires: first and last instant of it. */
    @Test
    void a_buenos_aires_month_includes_its_first_and_last_instants_and_nothing_beyond() {
        UUID student = issueUser(Role.STUDENT);
        UUID course = seedCourse("Salsa Intermedio");
        Instant from = Instant.parse("2026-11-01T03:00:00Z");
        Instant to = Instant.parse("2026-12-01T03:00:00Z");
        UUID lastOfOctober = seedSession(course, from.minusSeconds(1), "SCHEDULED");
        UUID firstOfNovember = seedSession(course, from, "SCHEDULED");
        UUID lastOfNovember = seedSession(course, to.minusSeconds(1), "SCHEDULED");
        UUID firstOfDecember = seedSession(course, to, "SCHEDULED");
        for (UUID session : List.of(lastOfOctober, firstOfNovember, lastOfNovember, firstOfDecember)) {
            seedAssignment(session, student);
        }

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = getAs(student, Role.STUDENT, range(from, to));

        assertThat(sessionsOf(response)).extracting(item -> item.get("sessionId"))
            .containsExactly(firstOfNovember.toString(), lastOfNovember.toString());
    }

    @Test
    void a_range_of_exactly_92_days_is_accepted() {
        UUID student = issueUser(Role.STUDENT);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response =
            getAs(student, Role.STUDENT, range(BA_DAY_FROM, BA_DAY_FROM.plus(92, ChronoUnit.DAYS)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessionsOf(response)).isEmpty();
    }

    /** {@code %2B} is how a client encodes the {@code +} of an offset; it is normalised to the UTC instant. */
    @Test
    void an_encoded_offset_is_normalised_to_utc_and_echoed_in_utc() {
        UUID student = issueUser(Role.STUDENT);
        UUID session = seedSession(seedCourse("Salsa Intermedio"), Instant.parse("2026-11-12T03:30:00Z"), "SCHEDULED");
        seedAssignment(session, student);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> response = getRaw(student, Role.STUDENT,
            URL + "?from=2026-11-12T00:00:00-03:00&to=2026-11-13T06%3A00%3A00%2B03%3A00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("from")).isEqualTo("2026-11-12T03:00:00Z");
        assertThat(response.getBody().get("to")).isEqualTo("2026-11-13T03:00:00Z");
        assertThat(sessionsOf(response)).extracting(item -> item.get("sessionId")).containsExactly(session.toString());
    }

    @Test
    void every_invalid_range_is_a_400_invalid_request_problem_with_a_fixed_detail() {
        UUID student = issueUser(Role.STUDENT);
        String atSecondOver92Days = BA_DAY_FROM.plus(92, ChronoUnit.DAYS).plusNanos(1).toString();
        List<String> invalidQueries = List.of(
            "",
            "?from=" + BA_DAY_FROM,
            "?to=" + BA_DAY_TO,
            "?month=2026-11",
            "?from=2026-11-12&to=" + BA_DAY_TO,
            "?from=" + BA_DAY_FROM + "&to=2026-11-13T03:00:00",
            "?from=hoy&to=" + BA_DAY_TO,
            // An unencoded + reaches the server as a space: not an instant.
            "?from=2026-11-12T00:00:00+03:00&to=" + BA_DAY_TO,
            "?from=" + BA_DAY_FROM + "&to=" + BA_DAY_FROM,
            "?from=" + BA_DAY_TO + "&to=" + BA_DAY_FROM,
            "?from=" + BA_DAY_FROM + "&to=" + atSecondOver92Days
        );

        for (String query : invalidQueries) {
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = getRaw(student, Role.STUDENT, URL + query);

            assertThat(response.getStatusCode()).as(query).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getHeaders().getContentType()).as(query)
                .isEqualTo(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(response.getBody().get("code")).as(query).isEqualTo("INVALID_REQUEST");
        }
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> tooWide = getRaw(
            student, Role.STUDENT, URL + "?from=" + BA_DAY_FROM + "&to=" + atSecondOver92Days
        );
        assertThat(tooWide.getBody().get("detail")).isEqualTo(RANGE_DETAIL);
    }
}
