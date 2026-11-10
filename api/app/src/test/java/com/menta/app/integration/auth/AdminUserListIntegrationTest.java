package com.menta.app.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import java.util.List;
import java.util.Map;
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
 * End-to-end coverage of {@code GET /api/v1/admin/users} (#378) against real MySQL: the listing is filtered by
 * role and text, paged, bounded and never exposes credentials.
 */
class AdminUserListIntegrationTest extends AbstractAdminUserIntegrationTest {

    private static final String USERS_URL = "/api/v1/admin/users";

    @Autowired private TestRestTemplate http;

    private String login(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/auth/login", HttpMethod.POST,
            new HttpEntity<>(Map.of("email", user.getEmail().getValue(), "password", RAW_PASSWORD), headers),
            Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("access_token");
    }

    private ResponseEntity<Map> list(String accessToken, String query) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return http.exchange(USERS_URL + query, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    @Test
    void lists_users_filtered_by_role_and_search_and_paged() {
        User admin = newUser(Role.ADMIN);
        newUser(Role.STUDENT);
        newUser(Role.STUDENT);
        newUser(Role.INSTRUCTOR);
        String token = login(admin);

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
