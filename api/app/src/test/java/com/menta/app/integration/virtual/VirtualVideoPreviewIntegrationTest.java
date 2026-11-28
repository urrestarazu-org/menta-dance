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
import com.menta.virtual.infrastructure.persistence.repository.VirtualVideoAuditJpaRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * {@code GET /api/v1/admin/virtual/videos/{videoId}/stream} (#864) against real MySQL, through the real
 * {@code SecurityConfig} chain, the real use case and the real token-authentication signer. The
 * {@code integration-test} profile selects the in-memory video library, so no test reaches Bunny.
 */
@ExtendWith(OutputCaptureExtension.class)
class VirtualVideoPreviewIntegrationTest extends CatalogAccessMocksIntegrationTestBase {

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

    private HttpHeaders headersFor(Role role) {
        User user = new User(
            UserId.of(seedUser(role)), Email.of("token@example.com"), "hash", role, UserStatus.ACTIVE, NOW, NOW
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessTokenIssuer.issue(user).token());
        return headers;
    }

    private void seedCourse(UUID professorId, String courseTitle, String videoId) {
        UUID courseId = UUID.randomUUID();
        courseRepository.save(new VirtualCourseJpaEntity(
            courseId, courseTitle, "corta", "larga", professorId, "https://cdn/img.jpg", "tango", "BEGINNER",
            false, CourseStatus.PUBLISHED, NOW, NOW
        ));
        UUID moduleId = UUID.randomUUID();
        moduleRepository.save(new VirtualModuleJpaEntity(moduleId, courseId, "Módulo de " + courseTitle, false, 0));
        lessonRepository.save(new VirtualLessonJpaEntity(
            UUID.randomUUID(), moduleId, courseId, "Lección de " + courseTitle, "d", videoId, 5, false, 0
        ));
    }

    private static LibraryVideo video(String id, LibraryVideoStatus status, boolean mp4) {
        return new LibraryVideo(id, "Título de " + id, status, 90, "https://cdn/" + id + ".jpg", mp4, List.of("720p"));
    }

    private ResponseEntity<Map> preview(String videoId, HttpHeaders headers) {
        return http.exchange(URL + videoId + "/stream", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(ResponseEntity<Map> response, String name) {
        return (Map<String, Object>) response.getBody().get(name);
    }

    // --- tests ----------------------------------------------------------

    @Test
    @DisplayName("el admin obtiene una URL firmada de un video que ninguna lección usa, y no queda auditoría")
    void the_admin_previews_a_video_no_lesson_uses_and_nothing_is_audited() {
        library.add(video("v-free", LibraryVideoStatus.FINISHED, true));
        Instant before = Instant.now();

        ResponseEntity<Map> response = preview("v-free", headersFor(Role.ADMIN));

        Instant after = Instant.now();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        Map<String, Object> stream = block(response, "stream");
        assertThat(stream.keySet()).containsExactlyInAnyOrder("url", "type", "expiresAt");
        assertThat((String) stream.get("url"))
            .startsWith("https://vz-test.b-cdn.net/v-free/play_720p.mp4?token=HS256-")
            .contains("&expires=");
        assertThat(stream.get("type")).isEqualTo("HLS");
        // ISO-8601 in UTC, and the same four hours as the student stream.
        String expiresAt = (String) stream.get("expiresAt");
        assertThat(expiresAt).endsWith("Z");
        Instant expires = Instant.parse(expiresAt);
        assertThat(expires).isBetween(before.plus(Duration.ofHours(4)).minusSeconds(1),
            after.plus(Duration.ofHours(4)).plusSeconds(1));
        assertThat((String) stream.get("url")).endsWith("&expires=" + expires.getEpochSecond());
        assertThat(block(response, "video")).containsEntry("videoId", "v-free")
            .containsEntry("title", "Título de v-free");
        assertThat(auditRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("el instructor previsualiza un video que usa solo otro instructor y uno que nadie usa")
    void the_instructor_previews_a_video_only_another_instructor_uses_and_one_nobody_uses() {
        seedCourse(seedUser(Role.INSTRUCTOR), "Salsa", "v-theirs");
        library.add(video("v-theirs", LibraryVideoStatus.FINISHED, true));
        library.add(video("v-free", LibraryVideoStatus.FINISHED, true));
        HttpHeaders instructor = headersFor(Role.INSTRUCTOR);

        for (String videoId : List.of("v-theirs", "v-free")) {
            ResponseEntity<Map> response = preview(videoId, instructor);

            assertThat(response.getStatusCode()).as(videoId).isEqualTo(HttpStatus.OK);
            assertThat((String) block(response, "stream").get("url")).as(videoId)
                .contains("/" + videoId + "/play_720p.mp4?token=");
        }
    }

    @Test
    @DisplayName("sin token responde 401 y con otro rol, 403")
    void an_anonymous_caller_gets_401_and_other_roles_get_403() {
        library.add(video("v-1", LibraryVideoStatus.FINISHED, true));

        assertThat(preview("v-1", new HttpHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        for (Role role : List.of(Role.STUDENT, Role.RECEPTIONIST)) {
            ResponseEntity<Map> response = preview("v-1", headersFor(role));

            assertThat(response.getStatusCode()).as(role.name()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody().toString()).as(role.name()).doesNotContain("token=");
        }
    }

    @Test
    @DisplayName("un video que la biblioteca no tiene responde 404 VIDEO_NOT_FOUND para los dos roles")
    void an_unknown_video_answers_404_video_not_found() {
        for (Role role : List.of(Role.ADMIN, Role.INSTRUCTOR)) {
            ResponseEntity<Map> response = preview("nope", headersFor(role));

            assertThat(response.getStatusCode()).as(role.name()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().get("code")).as(role.name()).isEqualTo("VIDEO_NOT_FOUND");
        }
    }

    @Test
    @DisplayName("un video que todavía no se reproduce responde 409 VIDEO_NOT_PLAYABLE con el motivo")
    void a_video_that_cannot_play_answers_409_video_not_playable_with_a_reason_and_nothing_else() {
        library.add(video("v-nomp4", LibraryVideoStatus.FINISHED, false));
        library.add(video("v-encoding", LibraryVideoStatus.TRANSCODING, true));
        library.add(video("v-waiting", LibraryVideoStatus.CREATED, false));
        library.add(video("v-broken", LibraryVideoStatus.ERROR, false));
        library.add(video("v-unknown", LibraryVideoStatus.UNKNOWN, true));
        HttpHeaders admin = headersFor(Role.ADMIN);

        Set<Object> details = new java.util.HashSet<>();
        for (String videoId : List.of("v-nomp4", "v-encoding", "v-waiting", "v-broken", "v-unknown")) {
            ResponseEntity<Map> response = preview(videoId, admin);

            assertThat(response.getStatusCode()).as(videoId).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().get("code")).as(videoId).isEqualTo("VIDEO_NOT_PLAYABLE");
            assertThat(response.getBody().keySet()).as(videoId)
                .containsExactlyInAnyOrder("type", "title", "status", "detail", "instance", "code");
            assertThat(response.getBody().get("detail").toString()).as(videoId)
                .doesNotContain("token=").doesNotContain("https://").doesNotContain("v-");
            details.add(response.getBody().get("detail"));
        }
        assertThat(details).hasSize(5);
    }

    @Test
    @DisplayName("una biblioteca caída responde 503 VIDEO_LIBRARY_UNAVAILABLE para los dos roles")
    void an_unavailable_library_answers_503_for_both_roles() {
        library.add(video("v-1", LibraryVideoStatus.FINISHED, true));
        library.simulateOutage(true);

        for (Role role : List.of(Role.ADMIN, Role.INSTRUCTOR)) {
            ResponseEntity<Map> response = preview("v-1", headersFor(role));

            assertThat(response.getStatusCode()).as(role.name()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().get("code")).as(role.name()).isEqualTo("VIDEO_LIBRARY_UNAVAILABLE");
            assertThat(response.getBody().keySet()).as(role.name())
                .containsExactlyInAnyOrder("type", "title", "status", "detail", "instance", "code");
        }
    }

    @Test
    @DisplayName("un id que no es un identificador simple responde 400 INVALID_REQUEST")
    void an_id_that_is_not_a_plain_identifier_answers_400_invalid_request() {
        HttpHeaders admin = headersFor(Role.ADMIN);

        for (String videoId : List.of("a.b", "a..b", "a@b")) {
            ResponseEntity<Map> response = preview(videoId, admin);

            assertThat(response.getStatusCode()).as(videoId).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("code")).as(videoId).isEqualTo("INVALID_REQUEST");
        }
    }

    @Test
    @DisplayName("la URL firmada no aparece en la salida del servidor")
    void the_signed_url_never_appears_in_the_captured_logs(CapturedOutput output) {
        library.add(video("v-log", LibraryVideoStatus.FINISHED, true));

        ResponseEntity<Map> response = preview("v-log", headersFor(Role.ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String url = (String) block(response, "stream").get("url");
        String token = url.substring(url.indexOf("token=") + "token=".length(), url.indexOf("&expires="));
        // Positive control: the signer's own secret-free line IS in the captured output, so the capture works.
        assertThat(output.getAll()).contains("Bunny.net signed URL: videoId=v-log host=vz-test.b-cdn.net");
        assertThat(output.getAll())
            .doesNotContain(url)
            .doesNotContain(token)
            .doesNotContain("token=HS256")
            .doesNotContain("test-only-security-key-not-a-secret");
    }
}
