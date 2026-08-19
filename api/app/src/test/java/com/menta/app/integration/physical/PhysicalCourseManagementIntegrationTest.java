package com.menta.app.integration.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import com.menta.shared.domain.vo.Email;
import java.time.Instant;
import java.time.LocalTime;
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

/**
 * HTTP-level coverage for US-PHYSICAL-005's management endpoints, through
 * the real {@code SecurityConfig} filter chain — this is what proves the new
 * {@code /api/v1/admin/physical/courses/**} matcher is declared before the
 * generic {@code /api/v1/admin/**} rule (a unit test on the controller alone
 * cannot exercise Spring's matcher-ordering behavior).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PhysicalCourseManagementIntegrationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private PhysicalCourseJpaRepository courseRepository;
    @Autowired private PhysicalSessionJpaRepository sessionRepository;
    @Autowired private PhysicalCapacityAssignmentJpaRepository assignmentRepository;

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
        assignmentRepository.deleteAll();
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

    private static Map<String, Object> createBody(String professorId) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
            "title", "Salsa inicial",
            "description", "Curso de salsa para nivel inicial",
            "professorName", "María García",
            "dayOfWeek", "WEDNESDAY",
            "startTime", "20:00:00",
            "durationMinutes", 60,
            "level", "INTERMEDIATE",
            "capacity", 20
        ));
        if (professorId != null) {
            body.put("professorId", professorId);
        }
        return body;
    }

    @Test
    void student_is_rejected_before_reaching_the_controller() {
        UUID studentId = issueUser(Role.STUDENT);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/courses", HttpMethod.POST,
            authenticated(studentId, Role.STUDENT, createBody(null)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void instructor_creating_a_course_is_always_its_own_professor() {
        UUID instructorId = issueUser(Role.INSTRUCTOR);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/courses", HttpMethod.POST,
            authenticated(instructorId, Role.INSTRUCTOR, createBody(null)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("professorId")).isEqualTo(instructorId.toString());
    }

    @Test
    void instructor_supplying_a_different_professor_id_is_rejected() {
        UUID instructorId = issueUser(Role.INSTRUCTOR);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/courses", HttpMethod.POST,
            authenticated(instructorId, Role.INSTRUCTOR, createBody(UUID.randomUUID().toString())), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("PROFESSOR_MISMATCH");
    }

    @Test
    void admin_can_assign_an_arbitrary_professor() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID targetProfessorId = UUID.randomUUID();

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/courses", HttpMethod.POST,
            authenticated(adminId, Role.ADMIN, createBody(targetProfessorId.toString())), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("professorId")).isEqualTo(targetProfessorId.toString());
    }

    @Test
    void instructor_cannot_patch_a_course_they_do_not_own() {
        UUID ownerId = UUID.randomUUID();
        UUID courseId = seedCourse(ownerId, CourseStatus.ACTIVE);
        UUID otherInstructorId = issueUser(Role.INSTRUCTOR);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenFor(otherInstructorId, Role.INSTRUCTOR));
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/courses/" + courseId, HttpMethod.PATCH,
            new HttpEntity<>(Map.of("title", "Nuevo título"), headers), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_OWNED");
    }

    @Test
    void admin_lists_every_course_regardless_of_owner() {
        seedCourse(UUID.randomUUID(), CourseStatus.ACTIVE);
        seedCourse(UUID.randomUUID(), CourseStatus.INACTIVE);
        UUID adminId = issueUser(Role.ADMIN);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenFor(adminId, Role.ADMIN));
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/courses", HttpMethod.GET, new HttpEntity<>(headers), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> courses = (List<?>) response.getBody().get("courses");
        assertThat(courses).hasSize(2);
    }

    @Test
    void deactivating_a_course_with_a_future_assigned_session_is_rejected() {
        UUID ownerId = UUID.randomUUID();
        UUID courseId = seedCourse(ownerId, CourseStatus.ACTIVE);
        UUID sessionId = UUID.randomUUID();
        sessionRepository.save(new PhysicalSessionJpaEntity(
            sessionId, courseId, Instant.now().plusSeconds(86400), 20, "SCHEDULED", null
        ));
        assignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), sessionId, UUID.randomUUID(), Instant.now()
        ));
        UUID adminId = issueUser(Role.ADMIN);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenFor(adminId, Role.ADMIN));
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/physical/courses/" + courseId, HttpMethod.PATCH,
            new HttpEntity<>(Map.of("status", "INACTIVE"), headers), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_HAS_ACTIVE_ASSIGNMENTS");
    }

    private UUID seedCourse(UUID professorId, CourseStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new PhysicalCourseJpaEntity(
            id, "Salsa inicial", "desc", professorId, "María García", "TUESDAY", LocalTime.of(19, 0),
            60, "BEGINNER", 20, status, now, now
        ));
        return id;
    }
}
