package com.menta.app.integration.virtual;

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
import com.menta.shared.domain.vo.Email;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import java.time.Instant;
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
 * HTTP-level coverage for US-VIRTUAL-006's management endpoints, through the
 * real {@code SecurityConfig} filter chain — this is what proves the three
 * new matchers (courses/modules/lessons) are declared before the generic
 * {@code /api/v1/admin/**} rule (a unit test on a controller alone cannot
 * exercise Spring's matcher-ordering behavior).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class VirtualCourseManagementIntegrationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private VirtualCourseJpaRepository courseRepository;
    @Autowired private VirtualModuleJpaRepository moduleRepository;
    @Autowired private VirtualLessonJpaRepository lessonRepository;

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
        lessonRepository.deleteAll();
        moduleRepository.deleteAll();
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

    private static Map<String, Object> createCourseBody(String professorId) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
            "title", "Tango Básico",
            "shortDescription", "Domina el tango argentino",
            "description", "Curso completo de técnicas fundamentales.",
            "imageUrl", "https://cdn/tango.jpg",
            "category", "tango",
            "level", "BEGINNER"
        ));
        if (professorId != null) {
            body.put("professorId", professorId);
        }
        return body;
    }

    private UUID seedCourse(UUID professorId, CourseStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new VirtualCourseJpaEntity(
            id, "Tango Básico", "corta", "larga", professorId, "https://cdn/img.jpg", "tango", "BEGINNER",
            false, status, now, now
        ));
        return id;
    }

    private UUID seedModule(UUID courseId, int order) {
        UUID id = UUID.randomUUID();
        moduleRepository.save(new VirtualModuleJpaEntity(id, courseId, "Módulo 1", order));
        return id;
    }

    private void seedCompleteLesson(UUID moduleId, UUID courseId) {
        lessonRepository.save(new VirtualLessonJpaEntity(
            UUID.randomUUID(), moduleId, courseId, "L1", "d", "bunny-123", 10, false, 0
        ));
    }

    @Test
    void student_is_rejected_before_reaching_the_controller() {
        UUID studentId = issueUser(Role.STUDENT);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/virtual/courses", HttpMethod.POST,
            new HttpEntity<>(createCourseBody(null), headersFor(studentId, Role.STUDENT)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void instructor_creating_a_course_is_always_its_own_professor() {
        UUID instructorId = issueUser(Role.INSTRUCTOR);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/virtual/courses", HttpMethod.POST,
            new HttpEntity<>(createCourseBody(null), headersFor(instructorId, Role.INSTRUCTOR)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("professorId")).isEqualTo(instructorId.toString());
        assertThat(response.getBody().get("status")).isEqualTo("DRAFT");
    }

    @Test
    void instructor_supplying_a_different_professor_id_is_rejected() {
        UUID instructorId = issueUser(Role.INSTRUCTOR);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/virtual/courses", HttpMethod.POST,
            new HttpEntity<>(createCourseBody(UUID.randomUUID().toString()), headersFor(instructorId, Role.INSTRUCTOR)),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("PROFESSOR_MISMATCH");
    }

    @Test
    void instructor_cannot_update_a_course_they_do_not_own() {
        UUID ownerId = UUID.randomUUID();
        UUID courseId = seedCourse(ownerId, CourseStatus.DRAFT);
        UUID otherInstructorId = issueUser(Role.INSTRUCTOR);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/virtual/courses/" + courseId, HttpMethod.PUT,
            new HttpEntity<>(Map.of("title", "Nuevo"), headersFor(otherInstructorId, Role.INSTRUCTOR)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_OWNED");
    }

    @Test
    void admin_lists_every_course_regardless_of_owner() {
        seedCourse(UUID.randomUUID(), CourseStatus.DRAFT);
        seedCourse(UUID.randomUUID(), CourseStatus.PUBLISHED);
        UUID adminId = issueUser(Role.ADMIN);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/virtual/courses", HttpMethod.GET,
            new HttpEntity<>(headersFor(adminId, Role.ADMIN)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> courses = (List<?>) response.getBody().get("courses");
        assertThat(courses).hasSize(2);
    }

    @Test
    void deleting_a_published_course_is_rejected() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID courseId = seedCourse(adminId, CourseStatus.PUBLISHED);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/virtual/courses/" + courseId, HttpMethod.DELETE,
            new HttpEntity<>(headersFor(adminId, Role.ADMIN)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_DRAFT");
    }

    @Test
    void deleting_a_draft_course_succeeds() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID courseId = seedCourse(adminId, CourseStatus.DRAFT);

        ResponseEntity<Void> response = http.exchange(
            "/api/v1/admin/virtual/courses/" + courseId, HttpMethod.DELETE,
            new HttpEntity<>(headersFor(adminId, Role.ADMIN)), Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void publishing_a_course_with_no_modules_is_rejected() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID courseId = seedCourse(adminId, CourseStatus.DRAFT);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/virtual/courses/" + courseId + "/publish", HttpMethod.PUT,
            new HttpEntity<>(headersFor(adminId, Role.ADMIN)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_PUBLISHABLE");
    }

    @Test
    void publishing_a_course_with_a_complete_lesson_succeeds() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID courseId = seedCourse(adminId, CourseStatus.DRAFT);
        UUID moduleId = seedModule(courseId, 0);
        seedCompleteLesson(moduleId, courseId);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/virtual/courses/" + courseId + "/publish", HttpMethod.PUT,
            new HttpEntity<>(headersFor(adminId, Role.ADMIN)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("PUBLISHED");
    }

    @Test
    void unpublishing_a_published_course_returns_it_to_draft() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID courseId = seedCourse(adminId, CourseStatus.PUBLISHED);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/virtual/courses/" + courseId + "/unpublish", HttpMethod.PUT,
            new HttpEntity<>(headersFor(adminId, Role.ADMIN)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("DRAFT");
    }

    @Test
    void creating_a_module_appends_at_the_end_when_order_is_omitted() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID courseId = seedCourse(adminId, CourseStatus.DRAFT);
        seedModule(courseId, 0);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/virtual/courses/" + courseId + "/modules", HttpMethod.POST,
            new HttpEntity<>(Map.of("title", "Módulo 2"), headersFor(adminId, Role.ADMIN)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("order")).isEqualTo(1);
    }

    @Test
    void instructor_cannot_add_a_module_to_a_course_they_do_not_own() {
        UUID ownerId = UUID.randomUUID();
        UUID courseId = seedCourse(ownerId, CourseStatus.DRAFT);
        UUID otherInstructorId = issueUser(Role.INSTRUCTOR);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/virtual/courses/" + courseId + "/modules", HttpMethod.POST,
            new HttpEntity<>(Map.of("title", "Módulo 2"), headersFor(otherInstructorId, Role.INSTRUCTOR)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_OWNED");
    }

    @Test
    void update_module_lives_under_its_own_prefix_and_resolves_ownership_via_the_parent_course() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID courseId = seedCourse(adminId, CourseStatus.DRAFT);
        UUID moduleId = seedModule(courseId, 0);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/virtual/modules/" + moduleId, HttpMethod.PUT,
            new HttpEntity<>(Map.of("title", "Nuevo título"), headersFor(adminId, Role.ADMIN)), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("title")).isEqualTo("Nuevo título");
    }

    @Test
    void create_lesson_and_update_lesson_live_under_their_own_prefixes() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID courseId = seedCourse(adminId, CourseStatus.DRAFT);
        UUID moduleId = seedModule(courseId, 0);

        ResponseEntity<Map> createResponse = http.exchange(
            "/api/v1/admin/virtual/modules/" + moduleId + "/lessons", HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "title", "L1", "description", "d", "videoId", "bunny-123", "durationMinutes", 10, "free", false
                ),
                headersFor(adminId, Role.ADMIN)
            ),
            Map.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String lessonId = (String) createResponse.getBody().get("lessonId");

        ResponseEntity<Map> updateResponse = http.exchange(
            "/api/v1/admin/virtual/lessons/" + lessonId, HttpMethod.PUT,
            new HttpEntity<>(Map.of("free", true), headersFor(adminId, Role.ADMIN)), Map.class
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().get("free")).isEqualTo(true);
    }

    @Test
    void reorder_modules_rejects_a_list_that_does_not_match_the_current_modules() {
        UUID adminId = issueUser(Role.ADMIN);
        UUID courseId = seedCourse(adminId, CourseStatus.DRAFT);
        seedModule(courseId, 0);

        ResponseEntity<Map> response = http.exchange(
            "/api/v1/admin/virtual/courses/" + courseId + "/modules/reorder", HttpMethod.PUT,
            new HttpEntity<>(Map.of("moduleIds", List.of(UUID.randomUUID().toString())),
                headersFor(adminId, Role.ADMIN)),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("MODULE_REORDER_MISMATCH");
    }
}
