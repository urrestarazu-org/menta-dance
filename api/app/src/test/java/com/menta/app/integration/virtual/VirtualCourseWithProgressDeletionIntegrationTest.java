package com.menta.app.integration.virtual;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.app.integration.support.CatalogAccessMocksIntegrationTestBase;
import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.infrastructure.persistence.entity.LessonProgressJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseAuditJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.LessonProgressJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseAuditJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code DELETE /api/v1/admin/virtual/courses/{courseId}} of a course with student progress (#834)
 * against real MySQL, through the real {@code SecurityConfig} chain and the real transactional use case.
 *
 * <p>Like {@link VirtualLessonDeletionIntegrationTest}, each test adds V19's
 * {@code fk_virtual_lesson_progress_lesson} (RESTRICT) to the schema Hibernate created, because
 * {@code create-drop} maps no relationships: without it a delete that left progress behind would pass
 * here and fail in production. The FK is dropped afterwards so the shared schema stays as the sibling
 * classes expect it.</p>
 */
class VirtualCourseWithProgressDeletionIntegrationTest extends CatalogAccessMocksIntegrationTestBase {

    private static final String COURSES_URL = "/api/v1/admin/virtual/courses/";
    private static final String PROGRESS_FK = "fk_virtual_lesson_progress_lesson";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private VirtualCourseJpaRepository courseRepository;
    @Autowired private VirtualModuleJpaRepository moduleRepository;
    @Autowired private VirtualLessonJpaRepository lessonRepository;
    @Autowired private LessonProgressJpaRepository progressRepository;
    @Autowired private VirtualCourseAuditJpaRepository auditRepository;

    @BeforeEach
    void addTheProgressForeignKeyOfV19() {
        jdbcTemplate.execute(
            "ALTER TABLE virtual_lesson_progress ADD CONSTRAINT " + PROGRESS_FK
                + " FOREIGN KEY (lesson_id) REFERENCES virtual_lessons (id)"
        );
    }

    @AfterEach
    void cleanUp() {
        progressRepository.deleteAll();
        lessonRepository.deleteAll();
        moduleRepository.deleteAll();
        courseRepository.deleteAll();
        auditRepository.deleteAll();
        jdbcTemplate.execute("ALTER TABLE virtual_lesson_progress DROP FOREIGN KEY " + PROGRESS_FK);
    }

    // --- fixtures -------------------------------------------------------

    private UUID seedUser(Role role) {
        User user = User.create(
            Email.of(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com"), "irrelevant-hash",
            role, NOW
        );
        userRepository.save(user);
        return user.getId().getValue();
    }

    private HttpHeaders headersFor(UUID userId, Role role) {
        User user = new User(
            UserId.of(userId), Email.of("token@example.com"), "hash", role, UserStatus.ACTIVE, NOW, NOW
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessTokenIssuer.issue(user).token());
        return headers;
    }

    private UUID seedCourse(UUID professorId, CourseStatus status) {
        UUID id = UUID.randomUUID();
        courseRepository.save(new VirtualCourseJpaEntity(
            id, "Tango Básico", "corta", "larga", professorId, "https://cdn/img.jpg", "tango", "BEGINNER",
            false, status, NOW, NOW
        ));
        return id;
    }

    /** Seeds a course with one module and one lesson with video; returns the lesson id. */
    private UUID seedCourseContent(UUID courseId) {
        UUID moduleId = UUID.randomUUID();
        moduleRepository.save(new VirtualModuleJpaEntity(moduleId, courseId, "Módulo 1", false, 0));
        UUID lessonId = UUID.randomUUID();
        lessonRepository.save(new VirtualLessonJpaEntity(
            lessonId, moduleId, courseId, "Lección 1", "d", "video-0", 10, false, 0
        ));
        return lessonId;
    }

    private void seedProgress(UUID courseId, UUID lessonId) {
        progressRepository.save(new LessonProgressJpaEntity(
            UUID.randomUUID(), UUID.randomUUID(), lessonId, courseId, 120, false, null, NOW, NOW, NOW
        ));
    }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, HttpHeaders headers) {
        return http.exchange(COURSES_URL + path, method, new HttpEntity<>(headers), Map.class);
    }

    private List<VirtualCourseAuditJpaEntity> deleteAuditRows() {
        return auditRepository.findAll().stream().filter(row -> "DELETE_COURSE".equals(row.getAction())).toList();
    }

    // --- tests ----------------------------------------------------------

    @Test
    void an_unpublished_course_whose_students_have_progress_is_rejected_with_409_and_nothing_is_deleted() {
        UUID adminId = seedUser(Role.ADMIN);
        HttpHeaders headers = headersFor(adminId, Role.ADMIN);
        UUID courseId = seedCourse(adminId, CourseStatus.PUBLISHED);
        UUID lessonId = seedCourseContent(courseId);
        seedProgress(courseId, lessonId);
        assertThat(exchange(courseId + "/unpublish", HttpMethod.PUT, headers).getStatusCode())
            .isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> response = exchange(courseId.toString(), HttpMethod.DELETE, headers);

        assertThat(response.getStatusCode()).as("body: %s", response.getBody()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_HAS_PROGRESS");
        assertThat(response.getBody().get("detail")).isEqualTo(
            "El curso no se puede borrar porque ya tiene progreso de alumnos."
        );
        assertThat(courseRepository.findById(courseId)).isPresent();
        assertThat(moduleRepository.findAll()).hasSize(1);
        assertThat(lessonRepository.findById(lessonId)).isPresent();
        assertThat(progressRepository.findAll()).hasSize(1);
        assertThat(deleteAuditRows()).isEmpty();
    }

    @Test
    void a_published_course_with_progress_keeps_answering_course_not_draft() {
        UUID adminId = seedUser(Role.ADMIN);
        UUID courseId = seedCourse(adminId, CourseStatus.PUBLISHED);
        seedProgress(courseId, seedCourseContent(courseId));

        ResponseEntity<Map> response =
            exchange(courseId.toString(), HttpMethod.DELETE, headersFor(adminId, Role.ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_DRAFT");
    }

    @Test
    void an_instructor_who_does_not_own_a_course_with_progress_gets_403_before_the_progress_check() {
        UUID courseId = seedCourse(UUID.randomUUID(), CourseStatus.DRAFT);
        seedProgress(courseId, seedCourseContent(courseId));

        ResponseEntity<Map> response =
            exchange(courseId.toString(), HttpMethod.DELETE, headersFor(seedUser(Role.INSTRUCTOR), Role.INSTRUCTOR));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_OWNED");
    }

    @Test
    void the_progress_of_another_course_does_not_block_deleting_a_draft_course_with_content() {
        UUID adminId = seedUser(Role.ADMIN);
        UUID otherCourseId = seedCourse(adminId, CourseStatus.PUBLISHED);
        seedProgress(otherCourseId, seedCourseContent(otherCourseId));
        UUID draftId = seedCourse(adminId, CourseStatus.DRAFT);
        UUID draftLessonId = seedCourseContent(draftId);

        ResponseEntity<Map> response = exchange(draftId.toString(), HttpMethod.DELETE, headersFor(adminId, Role.ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(courseRepository.findById(draftId)).isEmpty();
        assertThat(lessonRepository.findById(draftLessonId)).isEmpty();
        assertThat(courseRepository.findById(otherCourseId)).isPresent();
        assertThat(progressRepository.findAll()).hasSize(1);
        assertThat(deleteAuditRows()).hasSize(1);
    }
}
