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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
 * HTTP-level coverage for {@code GET /api/v1/admin/physical/sessions?from=&to=} (#355) through the real
 * {@code SecurityConfig} filter chain, the real use case and real MySQL. The ranges are fixed instants
 * (they are request parameters, in UTC like every backend date), so the suite cannot age out; only the
 * holds are relative to the real clock, because they expire against it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class PhysicalSessionsByRangeIntegrationTest extends AbstractPhysicalMySqlIntegrationTest {

    private static final String MERCHANT_ACCOUNT_ID = "merchant-integration-attendance-history";
    private static final String URL = "/api/v1/admin/physical/sessions";
    // The Buenos Aires day 2026-10-10 (UTC-3) as the client sends it: [03:00Z, 03:00Z of the next day).
    private static final String DAY_URL = rangeUrl("2026-10-10T03:00:00Z", "2026-10-11T03:00:00Z");

    private static String rangeUrl(String from, String to) {
        return URL + "?from=" + from + "&to=" + to;
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

    private UUID seedCourse(String title, int durationMinutes) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new PhysicalCourseJpaEntity(
            id, title, "desc", UUID.randomUUID(), "Ana Perez", "SATURDAY",
            LocalTime.of(20, 0), durationMinutes, "INTERMEDIATE", 20, CourseStatus.ACTIVE, now, now
        ));
        return id;
    }

    private UUID seedSession(UUID courseId, String scheduledAt, int capacity, String status) {
        UUID id = UUID.randomUUID();
        sessionRepository.save(
            new PhysicalSessionJpaEntity(id, courseId, Instant.parse(scheduledAt), capacity, status, null)
        );
        return id;
    }

    private void seedAssignment(UUID sessionId, AssignmentType type) {
        assignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), sessionId, UUID.randomUUID(), type, Instant.now()
        ));
    }

    private void seedHold(UUID sessionId, Instant expiresAt, Instant convertedAt) {
        holdRepository.save(new PhysicalCapacityHoldJpaEntity(
            UUID.randomUUID(), sessionId, UUID.randomUUID(), expiresAt, convertedAt, Instant.now()
        ));
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> getAs(Role role, String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(issueUser(role), role));
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Map<String, Object>> sessionsOf(ResponseEntity<Map> response) {
        return (List<Map<String, Object>>) response.getBody().get("sessions");
    }

    @Test
    void lists_the_sessions_of_the_range_of_every_course_ordered_by_start_then_course_name() {
        UUID salsa = seedCourse("Salsa Intermedio", 90);
        UUID bachata = seedCourse("Bachata Inicial", 60);
        UUID tango = seedCourse("Tango", 60);
        // All inside the range. Seeded out of order on purpose.
        final UUID eveningSalsa = seedSession(salsa, "2026-10-10T22:00:00Z", 20, "SCHEDULED");
        final UUID eveningBachata = seedSession(bachata, "2026-10-10T22:00:00Z", 15, "SCHEDULED");
        final UUID morningTango = seedSession(tango, "2026-10-10T13:00:00Z", 10, "SCHEDULED");
        // Outside the range, they do not leak in.
        seedSession(salsa, "2026-10-09T22:00:00Z", 20, "SCHEDULED");
        seedSession(salsa, "2026-10-11T22:00:00Z", 20, "SCHEDULED");

        ResponseEntity<Map> response = getAs(Role.ADMIN, DAY_URL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> sessions = sessionsOf(response);
        assertThat(sessions).extracting(item -> item.get("sessionId"))
            .containsExactly(morningTango.toString(), eveningBachata.toString(), eveningSalsa.toString());
        Map<String, Object> salsaItem = sessions.get(2);
        assertThat(salsaItem.get("courseId")).isEqualTo(salsa.toString());
        assertThat(salsaItem.get("courseName")).isEqualTo("Salsa Intermedio");
        assertThat(salsaItem.get("scheduledAt")).isEqualTo("2026-10-10T22:00:00Z");
        assertThat(salsaItem.get("endsAt")).isEqualTo("2026-10-10T23:30:00Z");
        assertThat(salsaItem.get("status")).isEqualTo("SCHEDULED");
        assertThat(salsaItem.get("capacity")).isEqualTo(20);
    }

    @Test
    void occupied_counts_assignments_and_a_pending_payments_hold_but_not_expired_or_converted_holds() {
        UUID course = seedCourse("Salsa Intermedio", 60);
        final UUID empty = seedSession(course, "2026-10-10T13:00:00Z", 20, "SCHEDULED");
        UUID busy = seedSession(course, "2026-10-10T22:00:00Z", 20, "SCHEDULED");
        Instant now = Instant.now();
        seedAssignment(busy, AssignmentType.MONTHLY);
        seedAssignment(busy, AssignmentType.INDIVIDUAL);
        // A pending payment holds its spot while the hold is live...
        seedHold(busy, now.plus(30, ChronoUnit.MINUTES), null);
        // ...but not once it expired or became an assignment.
        seedHold(busy, now.minus(30, ChronoUnit.MINUTES), null);
        seedHold(busy, now.plus(30, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES));

        ResponseEntity<Map> response = getAs(Role.RECEPTIONIST, DAY_URL);

        List<Map<String, Object>> sessions = sessionsOf(response);
        assertThat(sessions).extracting(item -> item.get("sessionId"))
            .containsExactly(empty.toString(), busy.toString());
        assertThat(sessions).extracting(item -> item.get("occupied")).containsExactly(0, 3);
        assertThat(sessions).extracting(item -> item.get("capacity")).containsExactly(20, 20);
    }

    @Test
    void a_cancelled_session_is_listed_with_its_status() {
        UUID cancelled = seedSession(seedCourse("Salsa", 60), "2026-10-10T22:00:00Z", 20, "CANCELLED");

        ResponseEntity<Map> response = getAs(Role.INSTRUCTOR, DAY_URL);

        assertThat(sessionsOf(response)).singleElement().satisfies(item -> {
            assertThat(item.get("sessionId")).isEqualTo(cancelled.toString());
            assertThat(item.get("status")).isEqualTo("CANCELLED");
        });
    }

    @Test
    void the_range_is_half_open_from_included_and_to_excluded() {
        UUID course = seedCourse("Salsa", 60);
        UUID beforeFrom = seedSession(course, "2026-10-10T02:59:59Z", 20, "SCHEDULED");
        UUID atFrom = seedSession(course, "2026-10-10T03:00:00Z", 20, "SCHEDULED");
        UUID beforeTo = seedSession(course, "2026-10-11T02:59:59Z", 20, "SCHEDULED");
        UUID atTo = seedSession(course, "2026-10-11T03:00:00Z", 20, "SCHEDULED");

        List<Map<String, Object>> thisRange = sessionsOf(getAs(Role.ADMIN, DAY_URL));
        List<Map<String, Object>> previous =
            sessionsOf(getAs(Role.ADMIN, rangeUrl("2026-10-09T03:00:00Z", "2026-10-10T03:00:00Z")));
        List<Map<String, Object>> next =
            sessionsOf(getAs(Role.ADMIN, rangeUrl("2026-10-11T03:00:00Z", "2026-10-12T03:00:00Z")));

        assertThat(thisRange).extracting(item -> item.get("sessionId"))
            .containsExactly(atFrom.toString(), beforeTo.toString());
        assertThat(previous).extracting(item -> item.get("sessionId")).containsExactly(beforeFrom.toString());
        assertThat(next).extracting(item -> item.get("sessionId")).containsExactly(atTo.toString());
    }

    @Test
    void an_offset_bound_is_the_same_instant_as_its_utc_form() {
        UUID course = seedCourse("Salsa", 60);
        UUID atFrom = seedSession(course, "2026-10-10T03:00:00Z", 20, "SCHEDULED");
        seedSession(course, "2026-10-11T03:00:00Z", 20, "SCHEDULED");

        ResponseEntity<Map> response =
            getAs(Role.ADMIN, rangeUrl("2026-10-10T00:00:00-03:00", "2026-10-11T00:00:00-03:00"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessionsOf(response)).extracting(item -> item.get("sessionId"))
            .containsExactly(atFrom.toString());
    }

    @Test
    void a_range_spanning_several_days_lists_every_one_of_them_in_order() {
        UUID course = seedCourse("Salsa", 60);
        UUID third = seedSession(course, "2026-10-12T22:00:00Z", 20, "SCHEDULED");
        UUID first = seedSession(course, "2026-10-10T22:00:00Z", 20, "SCHEDULED");
        UUID second = seedSession(course, "2026-10-11T22:00:00Z", 20, "SCHEDULED");
        seedSession(course, "2026-10-17T03:00:00Z", 20, "SCHEDULED");

        ResponseEntity<Map> response =
            getAs(Role.ADMIN, rangeUrl("2026-10-10T03:00:00Z", "2026-10-17T03:00:00Z"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessionsOf(response)).extracting(item -> item.get("sessionId"))
            .containsExactly(first.toString(), second.toString(), third.toString());
    }

    @Test
    void a_range_without_sessions_is_an_empty_list() {
        seedSession(seedCourse("Salsa", 60), "2026-10-12T22:00:00Z", 20, "SCHEDULED");

        ResponseEntity<Map> response = getAs(Role.ADMIN, DAY_URL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessionsOf(response)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "INSTRUCTOR", "RECEPTIONIST"})
    void admin_instructor_and_receptionist_may_list_the_range(Role role) {
        assertThat(getAs(role, DAY_URL).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void a_student_is_forbidden() {
        assertThat(getAs(Role.STUDENT, DAY_URL).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void a_request_without_a_token_is_unauthorized() {
        ResponseEntity<Map> response = http.getForEntity(DAY_URL, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** Reception reads the listing, but the rest of {@code /admin/physical/sessions/**} did not open to it. */
    @Test
    @SuppressWarnings("rawtypes")
    void a_receptionist_still_cannot_patch_a_session() {
        UUID receptionist = issueUser(Role.RECEPTIONIST);
        UUID session = seedSession(seedCourse("Salsa", 60), "2026-10-10T22:00:00Z", 20, "SCHEDULED");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(receptionist, Role.RECEPTIONIST));

        ResponseEntity<Map> response = http.exchange(
            URL + "/" + session, HttpMethod.PATCH, new HttpEntity<>(Map.of("capacity", 5), headers), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(sessionRepository.findById(session).orElseThrow().getCapacity()).isEqualTo(20);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void a_receptionist_cannot_post_to_the_listing_path() {
        UUID receptionist = issueUser(Role.RECEPTIONIST);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(receptionist, Role.RECEPTIONIST));

        ResponseEntity<Map> response = http.exchange(
            DAY_URL, HttpMethod.POST, new HttpEntity<>(Map.of(), headers), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        URL,
        URL + "?from=2026-10-10T03:00:00Z",
        URL + "?to=2026-10-11T03:00:00Z",
        URL + "?from=&to=2026-10-11T03:00:00Z",
        URL + "?from=2026-10-10T03:00:00Z&to=",
        URL + "?from=hoy&to=2026-10-11T03:00:00Z",
        URL + "?from=2026-10-10T03:00:00Z&to=mañana",
        URL + "?from=2026-10-10&to=2026-10-11",
        URL + "?from=2026-10-10T03:00:00&to=2026-10-11T03:00:00",
        URL + "?from=2026-02-30T03:00:00Z&to=2026-10-11T03:00:00Z"
    })
    @SuppressWarnings("rawtypes")
    void a_missing_or_malformed_bound_is_a_400_problem(String url) {
        ResponseEntity<Map> response = getAs(Role.ADMIN, url);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_REQUEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // from equal to to, and from after to.
        "2026-10-10T03:00:00Z|2026-10-10T03:00:00Z",
        "2026-10-11T03:00:00Z|2026-10-10T03:00:00Z",
        // One second over 7 days, and far over.
        "2026-10-10T03:00:00Z|2026-10-17T03:00:01Z",
        "2026-10-10T03:00:00Z|2027-10-10T03:00:00Z"
    })
    @SuppressWarnings("rawtypes")
    void an_empty_inverted_or_too_wide_range_is_a_400_problem(String bounds) {
        String[] range = bounds.split("\\|");

        ResponseEntity<Map> response = getAs(Role.ADMIN, rangeUrl(range[0], range[1]));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void a_range_of_exactly_seven_days_is_accepted() {
        assertThat(getAs(Role.ADMIN, rangeUrl("2026-10-10T03:00:00Z", "2026-10-17T03:00:00Z")).getStatusCode())
            .isEqualTo(HttpStatus.OK);
    }
}
