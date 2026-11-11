package com.menta.app.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.app.outbox.OutboxBlacklistReconciler;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.entity.UserAuditJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.UserAuditJpaRepository;
import com.menta.shared.domain.vo.Email;
import com.menta.shared.outbox.OutboxStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end coverage of {@code GET /api/v1/admin/users} and {@code PATCH /api/v1/admin/users/{id}/role} (#378)
 * against real MySQL: the role really changes, every session carrying the old role dies, the change is audited
 * and the last active administrator cannot be removed.
 */
class AdminUserRoleIntegrationTest extends AbstractAdminUserIntegrationTest {

    private static final String USERS_URL = "/api/v1/admin/users";
    private static final String ADMIN_PROBE = "/api/v1/admin/probe";

    @Autowired private TestRestTemplate http;
    @Autowired private OutboxBlacklistReconciler reconciler;
    @Autowired private UserAuditJpaRepository auditRepository;
    @Autowired private ObjectMapper objectMapper;
    @LocalServerPort private int port;

    private record Session(String accessToken, String refreshToken) {
    }

    private Session login(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/auth/login", HttpMethod.POST,
            new HttpEntity<>(Map.of("email", user.getEmail().getValue(), "password", RAW_PASSWORD), headers),
            Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new Session((String) response.getBody().get("access_token"),
            response.getHeaders().getFirst("X-Refresh-Token"));
    }

    private HttpResponse<String> patchRole(String accessToken, String userId, String body) throws Exception {
        // java.net.http, not TestRestTemplate: the default HttpURLConnection-based factory rejects PATCH.
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + USERS_URL + "/" + userId + "/role"))
            .header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(body));
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpStatus probeStatus(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return HttpStatus.valueOf(http.exchange(
            ADMIN_PROBE, HttpMethod.GET, new HttpEntity<>(headers), Map.class
        ).getStatusCode().value());
    }

    private ResponseEntity<Map> list(String accessToken, String query) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return http.exchange(USERS_URL + query, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private static String roleBody(Role role) {
        return "{\"role\":\"" + role + "\"}";
    }

    @Test
    void changing_a_role_closes_the_sessions_of_the_user_audits_the_change_and_publishes_the_event()
        throws Exception {
        User admin = newUser(Role.ADMIN);
        User student = newUser(Role.STUDENT);
        Session adminSession = login(admin);
        Session studentSession = login(student);
        long versionBefore = userRepository.findById(student.getId()).orElseThrow().getTokenVersion();
        assertThat(probeStatus(studentSession.accessToken())).isEqualTo(HttpStatus.FORBIDDEN);

        HttpResponse<String> response = patchRole(
            adminSession.accessToken(), student.getId().getValue().toString(), roleBody(Role.INSTRUCTOR)
        );

        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {});
        assertThat(body.get("id")).isEqualTo(student.getId().getValue().toString());
        assertThat(body.get("role")).isEqualTo("INSTRUCTOR");
        assertThat(body.keySet()).doesNotContain("passwordHash", "tokenVersion");

        User stored = userRepository.findById(student.getId()).orElseThrow();
        assertThat(stored.getRole()).isEqualTo(Role.INSTRUCTOR);
        assertThat(stored.getTokenVersion()).isEqualTo(versionBefore + 1);

        List<UserAuditJpaEntity> audit = auditRepository.findByUserIdOrderByCreatedAtAsc(student.getId().getValue());
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).getAction()).isEqualTo("ROLE_CHANGED");
        assertThat(audit.get(0).getActorId()).isEqualTo(admin.getId().getValue());
        assertThat(audit.get(0).getPreviousValue()).isEqualTo("STUDENT");
        assertThat(audit.get(0).getNewValue()).isEqualTo("INSTRUCTOR");
        assertThat(audit.get(0).getCreatedAt()).isNotNull();

        // Refresh tokens are revoked at once...
        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.set("X-Refresh-Token", studentSession.refreshToken());
        assertThat(http.exchange("/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(refreshHeaders), Map.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // ...and the access token carrying the old role dies as soon as the outbox event is projected.
        reconciler.processBatch();
        List<OutboxRowJpaEntity> events = outboxRowJpaRepository.findAll().stream()
            .filter(row -> "auth.UserRoleChanged".equals(row.getEventType())).toList();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getStatus()).isEqualTo(OutboxStatus.COMPLETED);
        assertThat(projectedVersions).containsEntry(student.getId().getValue().toString(), versionBefore + 1);
        assertThat(probeStatus(studentSession.accessToken())).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(probeStatus(adminSession.accessToken())).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void assigning_the_current_role_is_a_no_op_that_leaves_sessions_audit_and_events_alone() throws Exception {
        User admin = newUser(Role.ADMIN);
        User student = newUser(Role.STUDENT);
        final Session studentSession = login(student);
        long versionBefore = userRepository.findById(student.getId()).orElseThrow().getTokenVersion();

        HttpResponse<String> response = patchRole(
            login(admin).accessToken(), student.getId().getValue().toString(), roleBody(Role.STUDENT)
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(userRepository.findById(student.getId()).orElseThrow().getTokenVersion()).isEqualTo(versionBefore);
        assertThat(auditRepository.findByUserIdOrderByCreatedAtAsc(student.getId().getValue())).isEmpty();
        assertThat(outboxRowJpaRepository.findAll())
            .noneMatch(row -> "auth.UserRoleChanged".equals(row.getEventType()));
        reconciler.processBatch();
        assertThat(probeStatus(studentSession.accessToken())).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void the_last_active_admin_cannot_be_demoted_even_by_themselves() throws Exception {
        User onlyAdmin = newUser(Role.ADMIN);
        Session session = login(onlyAdmin);

        HttpResponse<String> response = patchRole(
            session.accessToken(), onlyAdmin.getId().getValue().toString(), roleBody(Role.STUDENT)
        );

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readValue(response.body(), Map.class))
            .containsEntry("code", "LAST_ADMIN_CANNOT_BE_DEMOTED");
        assertThat(userRepository.findById(onlyAdmin.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        assertThat(auditRepository.findAll()).isEmpty();
        assertThat(outboxRowJpaRepository.findAll())
            .noneMatch(row -> "auth.UserRoleChanged".equals(row.getEventType()));
    }

    @Test
    void an_admin_can_demote_themselves_while_another_active_admin_remains() throws Exception {
        User admin = newUser(Role.ADMIN);
        newUser(Role.ADMIN);

        HttpResponse<String> response = patchRole(
            login(admin).accessToken(), admin.getId().getValue().toString(), roleBody(Role.INSTRUCTOR)
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(Role.INSTRUCTOR);
    }

    @Test
    void an_inactive_admin_does_not_count_as_a_remaining_admin() throws Exception {
        User activeAdmin = newUser(Role.ADMIN);
        User inactiveAdmin = userRepository.save(User.rehydrate(
            UserId.generate(), Email.of("inactive-" + System.nanoTime() + "@example.com"),
            "hash", Role.ADMIN, UserStatus.INACTIVE, LocalDateTime.now(), LocalDateTime.now(), 1L
        ));

        HttpResponse<String> response = patchRole(
            login(activeAdmin).accessToken(), activeAdmin.getId().getValue().toString(), roleBody(Role.STUDENT)
        );

        assertThat(inactiveAdmin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(response.statusCode()).isEqualTo(409);
    }

    @Test
    void a_non_admin_cannot_change_roles() throws Exception {
        User instructor = newUser(Role.INSTRUCTOR);

        HttpResponse<String> response = patchRole(
            login(instructor).accessToken(), instructor.getId().getValue().toString(), roleBody(Role.ADMIN)
        );

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(userRepository.findById(instructor.getId()).orElseThrow().getRole()).isEqualTo(Role.INSTRUCTOR);
    }

    @Test
    void an_unauthenticated_caller_cannot_change_roles() throws Exception {
        assertThat(patchRole(null, UUID.randomUUID().toString(), roleBody(Role.ADMIN)).statusCode()).isEqualTo(401);
    }

    @Test
    void an_unknown_user_is_a_404_and_a_bad_body_is_a_400() throws Exception {
        String adminToken = login(newUser(Role.ADMIN)).accessToken();

        HttpResponse<String> unknown = patchRole(adminToken, UUID.randomUUID().toString(), roleBody(Role.STUDENT));
        HttpResponse<String> badRole = patchRole(adminToken, UUID.randomUUID().toString(), "{\"role\":\"SUPERADMIN\"}");

        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(objectMapper.readValue(unknown.body(), Map.class)).containsEntry("code", "USER_NOT_FOUND");
        assertThat(badRole.statusCode()).isEqualTo(400);
    }

    @Test
    void lists_users_filtered_by_role_and_search_and_paged() {
        newUser(Role.STUDENT);
        newUser(Role.STUDENT);
        newUser(Role.INSTRUCTOR);
        User admin = newUser(Role.ADMIN);
        String token = login(admin).accessToken();

        ResponseEntity<Map> all = list(token, "");
        ResponseEntity<Map> students = list(token, "?role=STUDENT");
        ResponseEntity<Map> bySearch = list(token, "?q=INSTRUCTOR-");
        ResponseEntity<Map> paged = list(token, "?size=1&page=1");
        ResponseEntity<Map> tooBig = list(token, "?size=51");

        assertThat(all.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(all.getBody()).containsEntry("totalElements", 4);
        assertThat((List<?>) students.getBody().get("items")).hasSize(2);
        assertThat((List<?>) bySearch.getBody().get("items")).hasSize(1);
        assertThat((List<?>) paged.getBody().get("items")).hasSize(1);
        assertThat(paged.getBody()).containsEntry("totalPages", 4).containsEntry("page", 1);
        Map<?, ?> firstItem = (Map<?, ?>) ((List<?>) all.getBody().get("items")).get(0);
        assertThat(firstItem.containsKey("passwordHash")).isFalse();
        assertThat(firstItem.containsKey("tokenVersion")).isFalse();
        assertThat(tooBig.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooBig.getBody()).containsEntry("code", "INVALID_REQUEST");
    }
}
