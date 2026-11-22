package com.menta.app.integration.virtual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code DELETE /api/v1/admin/virtual/lessons/{lessonId}} (#830) against real MySQL, through the real
 * {@code SecurityConfig} chain and the real transactional use case.
 *
 * <p>The {@code integration-test} profile builds the schema with Hibernate {@code create-drop}, which maps
 * no relationships and therefore creates none of Flyway's foreign keys. Each test adds V19's
 * {@code fk_virtual_lesson_progress_lesson} (RESTRICT) to the schema and drops it afterwards, so a delete
 * that left a progress row behind fails here exactly as it would in production instead of leaving a
 * silent orphan. Dropping it again keeps the shared schema of the cached context as the sibling classes
 * expect it.</p>
 */
class VirtualLessonDeletionIntegrationTest extends CatalogAccessMocksIntegrationTestBase {

    private static final String URL = "/api/v1/admin/virtual/lessons/";
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

    private UUID seedModule(UUID courseId) {
        UUID id = UUID.randomUUID();
        moduleRepository.save(new VirtualModuleJpaEntity(id, courseId, "Módulo 1", false, 0));
        return id;
    }

    /** Seeds one lesson per title, with display order 0..n-1; returns their ids in that order. */
    private List<UUID> seedLessons(UUID moduleId, UUID courseId, String... titles) {
        List<UUID> ids = new ArrayList<>();
        for (int order = 0; order < titles.length; order++) {
            UUID id = UUID.randomUUID();
            lessonRepository.save(new VirtualLessonJpaEntity(
                id, moduleId, courseId, titles[order], "d", "video-" + order, 10, false, order
            ));
            ids.add(id);
        }
        return ids;
    }

    private void seedProgress(UUID userId, UUID courseId, UUID lessonId, boolean completed) {
        progressRepository.save(new LessonProgressJpaEntity(
            UUID.randomUUID(), userId, lessonId, courseId, 120, completed, completed ? NOW : null, NOW, NOW, NOW
        ));
    }

    private ResponseEntity<Map> delete(String lessonId, HttpHeaders headers) {
        return http.exchange(URL + lessonId, HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);
    }

    private List<Integer> ordersOf(UUID moduleId) {
        return lessonRepository.findByModuleIdOrderByDisplayOrderAsc(moduleId).stream()
            .map(VirtualLessonJpaEntity::getDisplayOrder)
            .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> lessonIdsOfTheFirstModule(Map<?, ?> courseBody) {
        Map<String, Object> module = (Map<String, Object>) ((List<?>) courseBody.get("modules")).get(0);
        return ((List<Map<String, Object>>) module.get("lessons")).stream().map(lesson -> lesson.get("lessonId"))
            .toList();
    }

    // --- tests ----------------------------------------------------------

    @Test
    void the_foreign_key_is_active_so_a_lesson_with_progress_cannot_be_removed_on_its_own() {
        UUID courseId = seedCourse(UUID.randomUUID(), CourseStatus.PUBLISHED);
        UUID lessonId = seedLessons(seedModule(courseId), courseId, "L0").get(0);
        seedProgress(UUID.randomUUID(), courseId, lessonId, false);

        assertThatThrownBy(() -> lessonRepository.deleteById(lessonId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleting_a_lesson_of_a_published_course_removes_its_progress_and_renumbers_the_rest() {
        UUID courseId = seedCourse(UUID.randomUUID(), CourseStatus.PUBLISHED);
        UUID moduleId = seedModule(courseId);
        List<UUID> lessons = seedLessons(moduleId, courseId, "L0", "L1", "L2", "L3");
        UUID student = UUID.randomUUID();
        UUID otherStudent = UUID.randomUUID();
        seedProgress(student, courseId, lessons.get(0), true);
        seedProgress(student, courseId, lessons.get(1), true);
        seedProgress(otherStudent, courseId, lessons.get(1), false);
        UUID adminId = seedUser(Role.ADMIN);

        ResponseEntity<Map> response = delete(lessons.get(1).toString(), headersFor(adminId, Role.ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        assertThat(lessonRepository.findById(lessons.get(1))).isEmpty();
        assertThat(progressRepository.findAll()).extracting(LessonProgressJpaEntity::getLessonId)
            .containsExactly(lessons.get(0));
        assertThat(lessonRepository.findByModuleIdOrderByDisplayOrderAsc(moduleId))
            .extracting(VirtualLessonJpaEntity::getId)
            .containsExactly(lessons.get(0), lessons.get(2), lessons.get(3));
        assertThat(ordersOf(moduleId)).containsExactly(0, 1, 2);
        assertThat(courseRepository.findById(courseId).orElseThrow().getStatus()).isEqualTo(CourseStatus.PUBLISHED);
        List<VirtualCourseAuditJpaEntity> audit = auditRepository.findAll().stream()
            .filter(row -> "DELETE_LESSON".equals(row.getAction()))
            .toList();
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).getCourseId()).isEqualTo(courseId);
        assertThat(audit.get(0).getActorId()).isEqualTo(adminId);
        assertThat(audit.get(0).getPreviousValue()).contains("deletedProgressRows=2");
        assertThat(audit.get(0).getNewValue()).isNull();
    }

    @Test
    void the_deleted_lesson_disappears_from_the_management_detail_and_the_public_catalog() {
        UUID ownerId = seedUser(Role.INSTRUCTOR);
        UUID courseId = seedCourse(ownerId, CourseStatus.PUBLISHED);
        List<UUID> lessons = seedLessons(seedModule(courseId), courseId, "L0", "L1");

        assertThat(delete(lessons.get(0).toString(), headersFor(ownerId, Role.INSTRUCTOR)).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> detail = http.exchange(
            "/api/v1/admin/virtual/courses/" + courseId, HttpMethod.GET,
            new HttpEntity<>(headersFor(ownerId, Role.INSTRUCTOR)), Map.class
        );
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lessonIdsOfTheFirstModule(detail.getBody())).containsExactly(lessons.get(1).toString());
        ResponseEntity<Map> catalog =
            http.exchange("/api/v1/catalog/courses/" + courseId, HttpMethod.GET, null, Map.class);
        assertThat(catalog.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lessonIdsOfTheFirstModule(catalog.getBody())).containsExactly(lessons.get(1).toString());
    }

    @Test
    void an_instructor_cannot_delete_a_lesson_of_a_course_they_do_not_own() {
        UUID courseId = seedCourse(UUID.randomUUID(), CourseStatus.DRAFT);
        UUID moduleId = seedModule(courseId);
        List<UUID> lessons = seedLessons(moduleId, courseId, "L0", "L1");
        UUID otherInstructor = seedUser(Role.INSTRUCTOR);

        ResponseEntity<Map> response = delete(lessons.get(0).toString(), headersFor(otherInstructor, Role.INSTRUCTOR));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_OWNED");
        assertThat(lessonRepository.findById(lessons.get(0))).isPresent();
        assertThat(ordersOf(moduleId)).containsExactly(0, 1);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"STUDENT", "RECEPTIONIST"})
    void a_role_other_than_admin_or_instructor_is_rejected_by_the_security_chain(Role role) {
        UUID courseId = seedCourse(UUID.randomUUID(), CourseStatus.PUBLISHED);
        UUID lessonId = seedLessons(seedModule(courseId), courseId, "L0").get(0);
        UUID caller = seedUser(role);

        ResponseEntity<Map> response = delete(lessonId.toString(), headersFor(caller, role));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("ACCESS_DENIED");
        assertThat(lessonRepository.findById(lessonId)).isPresent();
    }

    @Test
    void an_anonymous_caller_is_401() {
        UUID courseId = seedCourse(UUID.randomUUID(), CourseStatus.PUBLISHED);
        UUID lessonId = seedLessons(seedModule(courseId), courseId, "L0").get(0);

        ResponseEntity<Map> response = delete(lessonId.toString(), new HttpHeaders());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(lessonRepository.findById(lessonId)).isPresent();
    }

    @Test
    void an_unknown_lesson_is_404_lesson_not_found() {
        UUID adminId = seedUser(Role.ADMIN);

        ResponseEntity<Map> response = delete(UUID.randomUUID().toString(), headersFor(adminId, Role.ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("LESSON_NOT_FOUND");
    }

    @Test
    void a_malformed_lesson_id_is_400_invalid_request() {
        UUID adminId = seedUser(Role.ADMIN);

        ResponseEntity<Map> response = delete("not-a-uuid", headersFor(adminId, Role.ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_REQUEST");
    }
}
