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
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.infrastructure.persistence.repository.PhysicalCoursePricingJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PhysicalCoursePricingRevisionJpaRepository;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.shared.domain.vo.Email;
import java.time.DayOfWeek;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * HTTP-level coverage for US-BILLING-009's pricing endpoints, through the
 * real {@code SecurityConfig} filter chain and the real cross-module
 * ownership port — this is what proves {@code PhysicalCourseOwnershipAdapter}
 * actually resolves ownership against Physical's own repository (not a mock)
 * and that the new GET/PUT matchers are declared correctly (a controller
 * unit test alone cannot exercise Spring's matcher-ordering behavior).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PhysicalCoursePricingIntegrationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private PhysicalCourseJpaRepository courseRepository;
    @Autowired private PhysicalCoursePricingJpaRepository pricingRepository;
    @Autowired private PhysicalCoursePricingRevisionJpaRepository revisionRepository;

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
        revisionRepository.deleteAll();
        pricingRepository.deleteAll();
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

    private UUID seedCourse(UUID professorId) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new PhysicalCourseJpaEntity(
            id, "Salsa Inicial", "Curso de salsa", professorId, "María García", DayOfWeek.WEDNESDAY.name(),
            LocalTime.of(20, 0), 60, "INTERMEDIATE", 20, CourseStatus.ACTIVE, now, now
        ));
        return id;
    }

    private static Map<String, Object> pricingBody(String monthlyPrice, String surcharge, String reason) {
        return Map.of(
            "monthlyPrice", monthlyPrice, "individualSurchargePercent", surcharge, "reason", reason
        );
    }

    @Test
    void the_owning_professor_publishes_a_new_pricing_version() {
        UUID professorId = issueUser(Role.INSTRUCTOR);
        UUID courseId = seedCourse(professorId);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/courses/" + courseId + "/pricing", HttpMethod.PUT,
            new HttpEntity<>(pricingBody("150.00", "10", "primer precio"), headersFor(professorId, Role.INSTRUCTOR)),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("version")).isEqualTo(1);
        assertThat(revisionRepository.findAll()).hasSize(1);
        assertThat(revisionRepository.findAll().get(0).getPreviousValue()).isNull();
    }

    @Test
    void a_second_update_increments_the_version_and_records_the_previous_snapshot() {
        UUID professorId = issueUser(Role.INSTRUCTOR);
        UUID courseId = seedCourse(professorId);
        http.exchange(
            "/api/v1/billing/physical/courses/" + courseId + "/pricing", HttpMethod.PUT,
            new HttpEntity<>(pricingBody("150.00", "10", "primer precio"), headersFor(professorId, Role.INSTRUCTOR)),
            Map.class
        );

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/courses/" + courseId + "/pricing", HttpMethod.PUT,
            new HttpEntity<>(pricingBody("180.00", "12", "ajuste de temporada"),
                headersFor(professorId, Role.INSTRUCTOR)),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("version")).isEqualTo(2);
        assertThat(revisionRepository.findAll()).hasSize(2);
    }

    @Test
    void another_instructor_cannot_update_a_course_they_do_not_own() {
        UUID ownerId = UUID.randomUUID();
        UUID courseId = seedCourse(ownerId);
        UUID otherInstructorId = issueUser(Role.INSTRUCTOR);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/courses/" + courseId + "/pricing", HttpMethod.PUT,
            new HttpEntity<>(pricingBody("150.00", "10", "motivo"), headersFor(otherInstructorId, Role.INSTRUCTOR)),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("PRICING_NOT_OWNED");
        assertThat(revisionRepository.findAll()).isEmpty();
    }

    @Test
    void an_invalid_surcharge_is_rejected_and_creates_no_revision() {
        UUID professorId = issueUser(Role.INSTRUCTOR);
        UUID courseId = seedCourse(professorId);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/courses/" + courseId + "/pricing", HttpMethod.PUT,
            new HttpEntity<>(pricingBody("150.00", "0", "motivo"), headersFor(professorId, Role.INSTRUCTOR)),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_INDIVIDUAL_SURCHARGE");
        assertThat(revisionRepository.findAll()).isEmpty();
        assertThat(pricingRepository.findAll()).isEmpty();
    }

    @Test
    void updating_pricing_for_a_course_that_does_not_exist_returns_404() {
        UUID professorId = issueUser(Role.INSTRUCTOR);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/courses/" + UUID.randomUUID() + "/pricing", HttpMethod.PUT,
            new HttpEntity<>(pricingBody("150.00", "10", "motivo"), headersFor(professorId, Role.INSTRUCTOR)),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("PHYSICAL_COURSE_NOT_FOUND");
    }

    @Test
    void an_admin_may_update_a_course_they_do_not_own() {
        UUID ownerId = UUID.randomUUID();
        UUID courseId = seedCourse(ownerId);
        UUID adminId = issueUser(Role.ADMIN);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/courses/" + courseId + "/pricing", HttpMethod.PUT,
            new HttpEntity<>(pricingBody("150.00", "10", "motivo"), headersFor(adminId, Role.ADMIN)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void a_student_is_rejected_before_reaching_the_controller() {
        UUID studentId = issueUser(Role.STUDENT);
        UUID courseId = seedCourse(UUID.randomUUID());

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/courses/" + courseId + "/pricing", HttpMethod.PUT,
            new HttpEntity<>(pricingBody("150.00", "10", "motivo"), headersFor(studentId, Role.STUDENT)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void get_requires_no_authentication_and_returns_the_published_pricing() {
        UUID professorId = issueUser(Role.INSTRUCTOR);
        UUID courseId = seedCourse(professorId);
        http.exchange(
            "/api/v1/billing/physical/courses/" + courseId + "/pricing", HttpMethod.PUT,
            new HttpEntity<>(pricingBody("150.00", "10", "motivo"), headersFor(professorId, Role.INSTRUCTOR)),
            Map.class
        );

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/courses/" + courseId + "/pricing", HttpMethod.GET, HttpEntity.EMPTY, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("version")).isEqualTo(1);
    }

    @Test
    void get_returns_404_when_no_pricing_was_ever_published() {
        UUID courseId = seedCourse(UUID.randomUUID());

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/billing/physical/courses/" + courseId + "/pricing", HttpMethod.GET, HttpEntity.EMPTY, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("PHYSICAL_COURSE_PRICING_NOT_FOUND");
    }
}
