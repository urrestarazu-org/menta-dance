package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.infrastructure.persistence.entity.PhysicalCoursePricingJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PhysicalCourseQuoteJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PhysicalCoursePricingJpaRepository;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import com.menta.shared.domain.vo.Email;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
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
 * HTTP-level coverage for US-BILLING-006's quote endpoint, through the real
 * {@code SecurityConfig} filter chain and the real cross-module {@code
 * PhysicalCourseAvailabilityAdapter} — proving it actually resolves
 * scheduled sessions against Physical's own repository (not a mock). {@link
 * Clock} is mocked so the "current month" is deterministic — fixed to
 * September and October 2026 (30 vs. 31 days, the DoD's normative pair)
 * instead of depending on whatever day the test happens to run.
 *
 * <p>MySQL via Testcontainers, not the plain {@code test} (H2) profile:
 * Physical's {@code findScheduledWithAvailability} native query projects a
 * {@code DATETIME} column onto an interface projection's {@code
 * Instant}-typed accessor, which H2 cannot convert ({@code
 * UnsupportedOperationException: Cannot project java.time.OffsetDateTime to
 * java.time.Instant}) — the same reason {@code
 * PhysicalCourseAvailabilityIntegrationTest} and {@code CatalogIntegrationTest}
 * already use real MySQL instead of H2.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class PhysicalCourseQuoteIntegrationTest {

    private static final Instant SEPTEMBER_NOW = Instant.parse("2026-09-15T12:00:00Z");
    private static final Instant OCTOBER_NOW = Instant.parse("2026-10-15T12:00:00Z");

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
    @Autowired private PhysicalCoursePricingJpaRepository pricingRepository;
    @Autowired private PhysicalCourseQuoteJpaRepository quoteRepository;

    // Every Redis-backed port needs a mock: integration-test excludes
    // RedisAutoConfiguration and this is one shared Spring context. Mirrors
    // CatalogIntegrationTest's exact mock set.
    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;
    @MockBean private Clock clock;
    // US-PHYSICAL-001: ProcessPhysicalCheckInUseCaseImpl needs a RedisTemplate
    // its bean factory would otherwise fail to resolve in this Redis-less context.
    @MockBean private ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;

    @AfterEach
    void cleanUp() {
        quoteRepository.deleteAll();
        pricingRepository.deleteAll();
        sessionRepository.deleteAll();
        courseRepository.deleteAll();
    }

    private UUID issueUser(Role role) {
        User user = User.create(
            Email.of(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com"), "irrelevant-hash", role
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

    private HttpHeaders headersFor(UUID userId, Role role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenFor(userId, role));
        return headers;
    }

    private UUID seedCourse() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new PhysicalCourseJpaEntity(
            id, "Salsa Inicial", "Curso de salsa", UUID.randomUUID(), "María García", DayOfWeek.WEDNESDAY.name(),
            LocalTime.of(20, 0), 60, "INTERMEDIATE", 20, CourseStatus.ACTIVE, now, now
        ));
        return id;
    }

    private void seedPricing(String courseId, String monthlyPrice, String surchargePercent) {
        pricingRepository.save(new PhysicalCoursePricingJpaEntity(
            courseId, new BigDecimal(monthlyPrice), "ARS", new BigDecimal(surchargePercent), 1, Instant.now()
        ));
    }

    private UUID seedSession(UUID courseId, Instant scheduledAt, int capacity) {
        UUID id = UUID.randomUUID();
        sessionRepository.save(new PhysicalSessionJpaEntity(id, courseId, scheduledAt, capacity, "SCHEDULED", null));
        return id;
    }

    private static Map<String, Object> quoteBody(String courseId, String purchaseType, String selectedSessionId) {
        Map<String, Object> body = new HashMap<>();
        body.put("courseId", courseId);
        body.put("purchaseType", purchaseType);
        if (selectedSessionId != null) {
            body.put("selectedSessionId", selectedSessionId);
        }
        return body;
    }

    @Test
    void monthly_quote_succeeds_with_available_sessions_in_september_2026() {
        when(clock.now()).thenReturn(SEPTEMBER_NOW);
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        seedPricing(courseId.toString(), "150.00", "10");
        seedSession(courseId, Instant.parse("2026-09-05T20:00:00Z"), 20);
        seedSession(courseId, Instant.parse("2026-09-12T20:00:00Z"), 20);
        seedSession(courseId, Instant.parse("2026-09-19T20:00:00Z"), 20);
        seedSession(courseId, Instant.parse("2026-09-26T20:00:00Z"), 20);
        // Just outside September must never be counted.
        seedSession(courseId, Instant.parse("2026-10-03T20:00:00Z"), 20);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/quotes", HttpMethod.POST,
            new HttpEntity<>(quoteBody(courseId.toString(), "MONTHLY", null), headersFor(studentId, Role.STUDENT)),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("purchaseType")).isEqualTo("MONTHLY");
        assertThat(response.getBody().get("scheduledSessionCount")).isEqualTo(4);
        assertThat(response.getBody().get("selectedSessionId")).isNull();
        assertThat(response.getBody().get("availability")).isEqualTo("AVAILABLE");
        assertThat(response.getBody().get("amount")).isEqualTo(150.00);
        assertThat(quoteRepository.findAll()).hasSize(1);
    }

    @Test
    void monthly_quote_succeeds_with_available_sessions_in_october_2026() {
        when(clock.now()).thenReturn(OCTOBER_NOW);
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        seedPricing(courseId.toString(), "150.00", "10");
        // October 2026 has 31 days -- five weekly sessions fit, unlike September's four.
        seedSession(courseId, Instant.parse("2026-10-07T20:00:00Z"), 20);
        seedSession(courseId, Instant.parse("2026-10-14T20:00:00Z"), 20);
        seedSession(courseId, Instant.parse("2026-10-21T20:00:00Z"), 20);
        seedSession(courseId, Instant.parse("2026-10-28T20:00:00Z"), 20);
        seedSession(courseId, Instant.parse("2026-10-02T20:00:00Z"), 20);
        // Just outside October must never be counted.
        seedSession(courseId, Instant.parse("2026-11-04T20:00:00Z"), 20);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/quotes", HttpMethod.POST,
            new HttpEntity<>(quoteBody(courseId.toString(), "MONTHLY", null), headersFor(studentId, Role.STUDENT)),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("scheduledSessionCount")).isEqualTo(5);
    }

    @Test
    void individual_quote_succeeds_and_applies_the_surcharge() {
        when(clock.now()).thenReturn(SEPTEMBER_NOW);
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        seedPricing(courseId.toString(), "100.00", "10");
        seedSession(courseId, Instant.parse("2026-09-05T20:00:00Z"), 20);
        seedSession(courseId, Instant.parse("2026-09-12T20:00:00Z"), 20);
        UUID selected = seedSession(courseId, Instant.parse("2026-09-19T20:00:00Z"), 20);
        seedSession(courseId, Instant.parse("2026-09-26T20:00:00Z"), 20);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/quotes", HttpMethod.POST,
            new HttpEntity<>(
                quoteBody(courseId.toString(), "INDIVIDUAL", selected.toString()), headersFor(studentId, Role.STUDENT)
            ),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("purchaseType")).isEqualTo("INDIVIDUAL");
        assertThat(response.getBody().get("scheduledSessionCount")).isEqualTo(4);
        assertThat(response.getBody().get("selectedSessionId")).isEqualTo(selected.toString());
        // 100.00 / 4 = 25.00 per session; +10% = 27.50
        assertThat(response.getBody().get("amount")).isEqualTo(27.50);
    }

    @Test
    void no_scheduled_sessions_in_the_period_returns_422() {
        when(clock.now()).thenReturn(SEPTEMBER_NOW);
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        seedPricing(courseId.toString(), "150.00", "10");
        // Only a session outside September's calendar month.
        seedSession(courseId, Instant.parse("2026-08-15T20:00:00Z"), 20);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/quotes", HttpMethod.POST,
            new HttpEntity<>(quoteBody(courseId.toString(), "MONTHLY", null), headersFor(studentId, Role.STUDENT)),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("code")).isEqualTo("NO_SCHEDULED_SESSIONS");
        assertThat(quoteRepository.findAll()).isEmpty();
    }

    @Test
    void individual_quote_with_a_surcharge_invisible_after_rounding_returns_422_and_persists_nothing() {
        when(clock.now()).thenReturn(SEPTEMBER_NOW);
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        // 10.00 spread across 100 daily-ish sessions, tiny surcharge: rounds away to nothing.
        seedPricing(courseId.toString(), "10.00", "0.5");
        UUID selected = null;
        for (int i = 0; i < 100; i++) {
            UUID sessionId = seedSession(courseId, Instant.parse("2026-09-01T00:00:00Z").plusSeconds(i * 60L), 20);
            if (i == 0) {
                selected = sessionId;
            }
        }

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/quotes", HttpMethod.POST,
            new HttpEntity<>(
                quoteBody(courseId.toString(), "INDIVIDUAL", selected.toString()), headersFor(studentId, Role.STUDENT)
            ),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("code")).isEqualTo("INDIVIDUAL_SURCHARGE_TOO_SMALL");
        assertThat(quoteRepository.findAll()).isEmpty();
    }

    @Test
    void unknown_selected_session_returns_404() {
        when(clock.now()).thenReturn(SEPTEMBER_NOW);
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        seedPricing(courseId.toString(), "150.00", "10");

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/quotes", HttpMethod.POST,
            new HttpEntity<>(
                quoteBody(courseId.toString(), "INDIVIDUAL", UUID.randomUUID().toString()),
                headersFor(studentId, Role.STUDENT)
            ),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("PHYSICAL_SESSION_NOT_FOUND");
    }

    @Test
    void quoting_without_authentication_is_rejected() {
        when(clock.now()).thenReturn(SEPTEMBER_NOW);
        UUID courseId = seedCourse();
        seedPricing(courseId.toString(), "150.00", "10");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/quotes", HttpMethod.POST,
            new HttpEntity<>(quoteBody(courseId.toString(), "MONTHLY", null), headers), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unavailable_projection_when_no_session_has_open_spots() {
        when(clock.now()).thenReturn(SEPTEMBER_NOW);
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse();
        seedPricing(courseId.toString(), "150.00", "10");
        // Zero capacity forces getAvailableSpots() to zero -- no need to model assignments.
        seedSession(courseId, Instant.parse("2026-09-05T20:00:00Z"), 0);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/quotes", HttpMethod.POST,
            new HttpEntity<>(quoteBody(courseId.toString(), "MONTHLY", null), headersFor(studentId, Role.STUDENT)),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("availability")).isEqualTo("UNAVAILABLE");
    }
}
