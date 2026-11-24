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
import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.LibraryVideoStatus;
import com.menta.virtual.application.port.out.VideoLibraryPort;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.infrastructure.cdn.local.InMemoryVideoLibrary;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualVideoAuditJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualVideoAuditJpaRepository;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * {@code PUT} and {@code DELETE /api/v1/admin/virtual/videos/{videoId}} (#841) against real MySQL, through the real
 * {@code SecurityConfig} chain and the real use cases. The {@code integration-test} profile selects the in-memory
 * video library, so no test reaches Bunny.
 */
class VirtualVideoManagementIntegrationTest extends CatalogAccessMocksIntegrationTestBase {

    private static final String URL = "/api/v1/admin/virtual/videos/";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private VideoLibraryPort videoLibrary;
    @Autowired private VirtualCourseJpaRepository courseRepository;
    @Autowired private VirtualModuleJpaRepository moduleRepository;
    @Autowired private VirtualLessonJpaRepository lessonRepository;
    @Autowired private VirtualVideoAuditJpaRepository auditRepository;

    private InMemoryVideoLibrary library;

    @BeforeEach
    void useTheInMemoryLibrary() {
        assertThat(videoLibrary).isInstanceOf(InMemoryVideoLibrary.class);
        library = (InMemoryVideoLibrary) videoLibrary;
        library.clear();
        auditRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        library.clear();
        auditRepository.deleteAll();
        lessonRepository.deleteAll();
        moduleRepository.deleteAll();
        courseRepository.deleteAll();
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
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /** Seeds a course with one module and one lesson per video id. */
    private void seedCourse(UUID professorId, String courseTitle, CourseStatus status, String... videoIds) {
        UUID courseId = UUID.randomUUID();
        courseRepository.save(new VirtualCourseJpaEntity(
            courseId, courseTitle, "corta", "larga", professorId, "https://cdn/img.jpg", "tango", "BEGINNER",
            false, status, NOW, NOW
        ));
        UUID moduleId = UUID.randomUUID();
        moduleRepository.save(new VirtualModuleJpaEntity(moduleId, courseId, "Módulo de " + courseTitle, false, 0));
        for (int order = 0; order < videoIds.length; order++) {
            lessonRepository.save(new VirtualLessonJpaEntity(
                UUID.randomUUID(), moduleId, courseId, "Lección " + order + " de " + courseTitle, "d", videoIds[order],
                5, false, order
            ));
        }
    }

    private static LibraryVideo video(String id, String title) {
        return new LibraryVideo(id, title, LibraryVideoStatus.FINISHED, 90, "https://cdn/" + id + ".jpg", true,
            List.of("720p"));
    }

    private ResponseEntity<Map> rename(String videoId, String title, HttpHeaders headers) {
        return http.exchange(
            URL + videoId, HttpMethod.PUT, new HttpEntity<>(Map.of("title", title), headers), Map.class
        );
    }

    private ResponseEntity<Map> delete(String videoId, HttpHeaders headers) {
        return http.exchange(URL + videoId, HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lessons(ResponseEntity<Map> response) {
        return (List<Map<String, Object>>) response.getBody().get("lessons");
    }

    private List<VirtualVideoAuditJpaEntity> audit() {
        return auditRepository.findAll();
    }

    // --- rename ---------------------------------------------------------

    @Test
    void the_admin_renames_a_video_no_lesson_uses_and_a_row_is_audited() {
        library.add(video("v-free", "Viejo"));
        UUID adminId = seedUser(Role.ADMIN);

        ResponseEntity<Map> response = rename("v-free", "  Nuevo  ", headersFor(adminId, Role.ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("videoId")).isEqualTo("v-free");
        assertThat(response.getBody().get("title")).isEqualTo("Nuevo");
        assertThat(lessons(response)).isEmpty();
        assertThat(library.find("v-free")).get().extracting(LibraryVideo::title).isEqualTo("Nuevo");
        assertThat(audit()).singleElement().satisfies(row -> {
            assertThat(row.getVideoId()).isEqualTo("v-free");
            assertThat(row.getActorId()).isEqualTo(adminId);
            assertThat(row.getAction()).isEqualTo("RENAME_VIDEO");
            assertThat(row.getPreviousValue()).isEqualTo("title=Viejo");
            assertThat(row.getNewValue()).isEqualTo("title=Nuevo");
            assertThat(row.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void the_instructor_renames_a_video_of_their_own_lessons_and_only_sees_their_own_lessons() {
        UUID instructorId = seedUser(Role.INSTRUCTOR);
        UUID otherInstructorId = seedUser(Role.INSTRUCTOR);
        seedCourse(instructorId, "Tango", CourseStatus.DRAFT, "shared");
        seedCourse(otherInstructorId, "Salsa", CourseStatus.PUBLISHED, "shared");
        library.add(video("shared", "Viejo"));

        ResponseEntity<Map> response = rename("shared", "Compartido", headersFor(instructorId, Role.INSTRUCTOR));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("title")).isEqualTo("Compartido");
        assertThat(lessons(response)).extracting(lesson -> lesson.get("courseTitle")).containsExactly("Tango");
        assertThat(audit()).singleElement().satisfies(row -> assertThat(row.getActorId()).isEqualTo(instructorId));
    }

    @Test
    void the_instructor_cannot_rename_the_video_of_someone_elses_lessons_nor_one_no_lesson_uses() {
        UUID otherInstructorId = seedUser(Role.INSTRUCTOR);
        seedCourse(otherInstructorId, "Salsa", CourseStatus.PUBLISHED, "theirs");
        library.add(video("theirs", "Ajeno"));
        library.add(video("unused", "Suelto"));
        UUID instructorId = seedUser(Role.INSTRUCTOR);

        for (String videoId : List.of("theirs", "unused", "unknown")) {
            ResponseEntity<Map> response = rename(videoId, "Mío", headersFor(instructorId, Role.INSTRUCTOR));

            assertThat(response.getStatusCode()).as(videoId).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody().get("code")).as(videoId).isEqualTo("VIDEO_NOT_OWNED");
        }
        assertThat(library.find("theirs")).get().extracting(LibraryVideo::title).isEqualTo("Ajeno");
        assertThat(library.find("unused")).get().extracting(LibraryVideo::title).isEqualTo("Suelto");
        assertThat(audit()).isEmpty();
    }

    @Test
    void renaming_a_video_the_library_does_not_have_answers_404_video_not_found() {
        UUID adminId = seedUser(Role.ADMIN);
        UUID instructorId = seedUser(Role.INSTRUCTOR);
        seedCourse(instructorId, "Tango", CourseStatus.PUBLISHED, "vanished");

        ResponseEntity<Map> asAdmin = rename("nope", "Nuevo", headersFor(adminId, Role.ADMIN));
        ResponseEntity<Map> asInstructor = rename("vanished", "Nuevo", headersFor(instructorId, Role.INSTRUCTOR));

        assertThat(asAdmin.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(asAdmin.getBody().get("code")).isEqualTo("VIDEO_NOT_FOUND");
        assertThat(asInstructor.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(audit()).isEmpty();
    }

    @Test
    void an_invalid_title_or_id_answers_400_invalid_request_and_touches_nothing() {
        library.add(video("v-1", "Viejo"));
        HttpHeaders admin = headersFor(seedUser(Role.ADMIN), Role.ADMIN);

        for (String title : List.of("", "   ", "a".repeat(256))) {
            ResponseEntity<Map> response = rename("v-1", title, admin);

            assertThat(response.getStatusCode()).as("title length " + title.length()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("code")).isEqualTo("INVALID_REQUEST");
        }
        ResponseEntity<Map> missing = http.exchange(
            URL + "v-1", HttpMethod.PUT, new HttpEntity<>("{}", admin), Map.class
        );
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rename("a b", "Nuevo", admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(library.find("v-1")).get().extracting(LibraryVideo::title).isEqualTo("Viejo");
        assertThat(audit()).isEmpty();
    }

    @Test
    void renaming_with_an_unavailable_library_answers_503_and_audits_nothing() {
        UUID adminId = seedUser(Role.ADMIN);
        library.add(video("v-1", "Viejo"));
        library.simulateOutage(true);

        ResponseEntity<Map> response = rename("v-1", "Nuevo", headersFor(adminId, Role.ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("code")).isEqualTo("VIDEO_LIBRARY_UNAVAILABLE");
        assertThat(audit()).isEmpty();
    }

    @Test
    void a_student_and_an_anonymous_caller_cannot_rename() {
        library.add(video("v-1", "Viejo"));
        HttpHeaders student = headersFor(seedUser(Role.STUDENT), Role.STUDENT);
        HttpHeaders anonymous = new HttpHeaders();
        anonymous.setContentType(MediaType.APPLICATION_JSON);

        assertThat(rename("v-1", "Nuevo", student).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rename("v-1", "Nuevo", anonymous).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(audit()).isEmpty();
    }

    // --- delete ---------------------------------------------------------

    @Test
    void the_admin_deletes_a_video_no_lesson_uses_and_deleting_it_again_is_not_an_error() {
        library.add(video("v-free", "Suelto"));
        UUID adminId = seedUser(Role.ADMIN);
        HttpHeaders admin = headersFor(adminId, Role.ADMIN);

        ResponseEntity<Map> first = delete("v-free", admin);
        ResponseEntity<Map> second = delete("v-free", admin);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(library.find("v-free")).isEmpty();
        assertThat(audit()).hasSize(2).allSatisfy(row -> {
            assertThat(row.getVideoId()).isEqualTo("v-free");
            assertThat(row.getActorId()).isEqualTo(adminId);
            assertThat(row.getAction()).isEqualTo("DELETE_VIDEO");
            assertThat(row.getNewValue()).isNull();
        });
        assertThat(audit()).extracting(VirtualVideoAuditJpaEntity::getPreviousValue)
            .containsExactlyInAnyOrder("videoId=v-free, title=Suelto", "videoId=v-free, missingInLibrary=true");
    }

    @Test
    @SuppressWarnings("unchecked")
    void a_video_used_by_a_lesson_of_any_course_in_any_status_is_not_deleted_and_the_409_lists_the_lessons() {
        UUID instructorId = seedUser(Role.INSTRUCTOR);
        UUID otherInstructorId = seedUser(Role.INSTRUCTOR);
        seedCourse(instructorId, "Tango", CourseStatus.DRAFT, "v-used");
        seedCourse(otherInstructorId, "Salsa", CourseStatus.PUBLISHED, "v-used");
        library.add(video("v-used", "Usado"));
        UUID adminId = seedUser(Role.ADMIN);

        ResponseEntity<Map> response = delete("v-used", headersFor(adminId, Role.ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("VIDEO_IN_USE");
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        List<Map<String, Object>> lessons = (List<Map<String, Object>>) response.getBody().get("lessons");
        assertThat(lessons).extracting(lesson -> lesson.get("courseTitle")).containsExactly("Salsa", "Tango");
        assertThat(lessons).extracting(lesson -> lesson.get("moduleTitle"))
            .containsExactly("Módulo de Salsa", "Módulo de Tango");
        assertThat(lessons).extracting(lesson -> lesson.get("lessonTitle"))
            .containsExactly("Lección 0 de Salsa", "Lección 0 de Tango");
        assertThat(lessons.get(0)).containsKeys("courseId", "moduleId", "lessonId");
        assertThat(library.find("v-used")).isPresent();
        assertThat(lessonRepository.findAll()).extracting(VirtualLessonJpaEntity::getVideoId)
            .containsExactly("v-used", "v-used");
        assertThat(audit()).isEmpty();
    }

    @Test
    void an_in_use_video_is_rejected_without_calling_the_library_even_when_the_library_is_down() {
        UUID instructorId = seedUser(Role.INSTRUCTOR);
        seedCourse(instructorId, "Tango", CourseStatus.PUBLISHED, "v-used");
        UUID adminId = seedUser(Role.ADMIN);
        library.simulateOutage(true);

        ResponseEntity<Map> response = delete("v-used", headersFor(adminId, Role.ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("VIDEO_IN_USE");
    }

    @Test
    void the_instructor_can_never_delete_not_even_a_video_no_lesson_uses() {
        UUID instructorId = seedUser(Role.INSTRUCTOR);
        seedCourse(instructorId, "Tango", CourseStatus.PUBLISHED, "mine");
        library.add(video("mine", "Mío"));
        library.add(video("unused", "Suelto"));
        HttpHeaders instructor = headersFor(instructorId, Role.INSTRUCTOR);

        assertThat(delete("mine", instructor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(delete("unused", instructor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(library.find("mine")).isPresent();
        assertThat(library.find("unused")).isPresent();
        assertThat(audit()).isEmpty();
    }

    @Test
    void a_student_and_an_anonymous_caller_cannot_delete() {
        library.add(video("v-1", "Suelto"));

        assertThat(delete("v-1", headersFor(seedUser(Role.STUDENT), Role.STUDENT)).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(delete("v-1", new HttpHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(library.find("v-1")).isPresent();
    }

    @Test
    void deleting_with_an_unavailable_library_answers_503_and_audits_nothing() {
        UUID adminId = seedUser(Role.ADMIN);
        library.add(video("v-1", "Suelto"));
        library.simulateOutage(true);

        ResponseEntity<Map> response = delete("v-1", headersFor(adminId, Role.ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("code")).isEqualTo("VIDEO_LIBRARY_UNAVAILABLE");
        assertThat(audit()).isEmpty();
    }

    @Test
    void deleting_with_an_id_that_is_not_a_plain_identifier_answers_400() {
        HttpHeaders admin = headersFor(seedUser(Role.ADMIN), Role.ADMIN);

        ResponseEntity<Map> response = delete("a.b", admin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_REQUEST");
    }
}
