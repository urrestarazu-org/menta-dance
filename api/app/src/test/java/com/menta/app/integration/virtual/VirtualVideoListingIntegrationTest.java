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

/**
 * {@code GET /api/v1/admin/virtual/videos} (#840) against real MySQL, through the real {@code SecurityConfig}
 * chain and the real use case. The {@code integration-test} profile selects the in-memory video library, so no
 * test reaches Bunny.
 */
class VirtualVideoListingIntegrationTest extends CatalogAccessMocksIntegrationTestBase {

    private static final String URL = "/api/v1/admin/virtual/videos";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private VideoLibraryPort videoLibrary;
    @Autowired private VirtualCourseJpaRepository courseRepository;
    @Autowired private VirtualModuleJpaRepository moduleRepository;
    @Autowired private VirtualLessonJpaRepository lessonRepository;

    private InMemoryVideoLibrary library;

    @BeforeEach
    void useTheInMemoryLibrary() {
        assertThat(videoLibrary).isInstanceOf(InMemoryVideoLibrary.class);
        library = (InMemoryVideoLibrary) videoLibrary;
        library.clear();
    }

    @AfterEach
    void cleanUp() {
        library.clear();
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
        return headers;
    }

    /** Seeds a course with one module and one lesson per video id; returns the lesson ids. */
    private List<UUID> seedCourse(UUID professorId, String courseTitle, String... videoIds) {
        UUID courseId = UUID.randomUUID();
        courseRepository.save(new VirtualCourseJpaEntity(
            courseId, courseTitle, "corta", "larga", professorId, "https://cdn/img.jpg", "tango", "BEGINNER",
            false, CourseStatus.PUBLISHED, NOW, NOW
        ));
        UUID moduleId = UUID.randomUUID();
        moduleRepository.save(new VirtualModuleJpaEntity(moduleId, courseId, "Módulo de " + courseTitle, false, 0));
        java.util.ArrayList<UUID> lessons = new java.util.ArrayList<>();
        for (int order = 0; order < videoIds.length; order++) {
            UUID lessonId = UUID.randomUUID();
            lessonRepository.save(new VirtualLessonJpaEntity(
                lessonId, moduleId, courseId, "Lección " + order + " de " + courseTitle, "d", videoIds[order], 5,
                false, order
            ));
            lessons.add(lessonId);
        }
        return lessons;
    }

    private static LibraryVideo video(String id, String title) {
        return new LibraryVideo(id, title, LibraryVideoStatus.FINISHED, 90, "https://cdn/" + id + ".jpg", true,
            List.of("720p"));
    }

    private ResponseEntity<Map> get(String query, HttpHeaders headers) {
        return http.exchange(URL + query, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(ResponseEntity<Map> response) {
        return (List<Map<String, Object>>) response.getBody().get("items");
    }

    // --- tests ----------------------------------------------------------

    @Test
    void the_admin_sees_the_whole_library_with_the_lessons_that_use_each_video() {
        UUID instructorId = seedUser(Role.INSTRUCTOR);
        seedCourse(instructorId, "Tango", "v-used");
        library.add(video("v-used", "Usado"));
        library.add(video("v-free", "Sin lecciones"));
        UUID adminId = seedUser(Role.ADMIN);

        ResponseEntity<Map> response = get("?size=10", headersFor(adminId, Role.ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("totalElements")).isEqualTo(2);
        assertThat(items(response)).extracting(item -> item.get("videoId")).containsExactly("v-free", "v-used");
        Map<String, Object> used = items(response).get(1);
        assertThat(used.get("title")).isEqualTo("Usado");
        assertThat(used.get("status")).isEqualTo("FINISHED");
        assertThat(used.get("missingInLibrary")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lessons = (List<Map<String, Object>>) used.get("lessons");
        assertThat(lessons).hasSize(1);
        assertThat(lessons.get(0).get("courseTitle")).isEqualTo("Tango");
        assertThat(lessons.get(0).get("moduleTitle")).isEqualTo("Módulo de Tango");
        assertThat(lessons.get(0).get("lessonTitle")).isEqualTo("Lección 0 de Tango");
        assertThat(items(response).get(0).get("lessons")).isEqualTo(List.of());
    }

    @Test
    void the_admin_also_sees_the_videos_lessons_use_that_the_library_no_longer_has_flagged_as_missing() {
        UUID instructorId = seedUser(Role.INSTRUCTOR);
        seedCourse(instructorId, "Tango", "gone", "here");
        library.add(video("here", "Acá"));
        library.add(video("unused", "Suelto"));
        UUID adminId = seedUser(Role.ADMIN);

        ResponseEntity<Map> response = get("", headersFor(adminId, Role.ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("totalElements")).isEqualTo(3);
        assertThat(items(response)).extracting(item -> item.get("videoId"))
            .containsExactly("unused", "here", "gone");
        Map<String, Object> gone = items(response).get(2);
        assertThat(gone.get("missingInLibrary")).isEqualTo(true);
        assertThat(gone.get("title")).isNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lessons = (List<Map<String, Object>>) gone.get("lessons");
        assertThat(lessons).hasSize(1);
        assertThat(lessons.get(0).get("courseTitle")).isEqualTo("Tango");
        assertThat(lessons.get(0).get("moduleTitle")).isEqualTo("Módulo de Tango");
        assertThat(lessons.get(0).get("lessonTitle")).isEqualTo("Lección 0 de Tango");
        assertThat(items(response).get(0).get("missingInLibrary")).isEqualTo(false);
        assertThat(items(response).get(0).get("lessons")).isEqualTo(List.of());
    }

    @Test
    void the_instructor_only_sees_the_videos_of_their_own_lessons_and_a_vanished_video_is_flagged() {
        UUID instructorId = seedUser(Role.INSTRUCTOR);
        UUID otherInstructorId = seedUser(Role.INSTRUCTOR);
        seedCourse(instructorId, "Tango", "mine", "shared", "vanished");
        seedCourse(otherInstructorId, "Salsa", "theirs", "shared");
        library.add(video("mine", "Mío"));
        library.add(video("shared", "Compartido"));
        library.add(video("theirs", "Ajeno"));
        library.add(video("unused", "Sin usar"));

        ResponseEntity<Map> response = get("", headersFor(instructorId, Role.INSTRUCTOR));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(items(response)).extracting(item -> item.get("videoId"))
            .containsExactly("shared", "mine", "vanished");
        assertThat(response.getBody().get("totalElements")).isEqualTo(3);
        Map<String, Object> vanished = items(response).get(2);
        assertThat(vanished.get("missingInLibrary")).isEqualTo(true);
        assertThat(vanished.get("title")).isNull();
        Map<String, Object> shared = items(response).get(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sharedLessons = (List<Map<String, Object>>) shared.get("lessons");
        assertThat(sharedLessons).extracting(lesson -> lesson.get("courseTitle")).containsExactly("Tango");
    }

    @Test
    void the_search_and_the_order_reach_the_library_for_both_roles() {
        UUID instructorId = seedUser(Role.INSTRUCTOR);
        seedCourse(instructorId, "Tango", "a", "b");
        library.add(video("a", "Tango Zeta"));
        library.add(video("b", "Bachata"));
        library.add(video("c", "Tango Alfa"));
        UUID adminId = seedUser(Role.ADMIN);

        ResponseEntity<Map> asAdmin = get("?search=tango&orderBy=title", headersFor(adminId, Role.ADMIN));
        ResponseEntity<Map> asInstructor =
            get("?search=tango&orderBy=title", headersFor(instructorId, Role.INSTRUCTOR));

        assertThat(items(asAdmin)).extracting(item -> item.get("videoId")).containsExactly("c", "a");
        assertThat(items(asInstructor)).extracting(item -> item.get("videoId")).containsExactly("a");
    }

    @Test
    void a_student_and_an_anonymous_caller_cannot_list_the_videos() {
        UUID studentId = seedUser(Role.STUDENT);

        assertThat(get("", headersFor(studentId, Role.STUDENT)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("", new HttpHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void a_bad_order_or_size_answers_400_invalid_request() {
        UUID adminId = seedUser(Role.ADMIN);

        for (String query : List.of("?orderBy=size", "?size=9", "?size=101", "?page=-1", "?page=abc")) {
            ResponseEntity<Map> response = get(query, headersFor(adminId, Role.ADMIN));

            assertThat(response.getStatusCode()).as(query).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("code")).as(query).isEqualTo("INVALID_REQUEST");
        }
    }

    @Test
    void an_unavailable_library_answers_503_with_its_own_code() {
        UUID adminId = seedUser(Role.ADMIN);
        library.simulateOutage(true);

        ResponseEntity<Map> response = get("", headersFor(adminId, Role.ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("code")).isEqualTo("VIDEO_LIBRARY_UNAVAILABLE");
    }
}
