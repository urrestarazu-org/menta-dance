package com.menta.app.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserProfile;
import com.menta.auth.domain.model.UserStatus;
import com.menta.shared.domain.vo.Email;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * End-to-end coverage of {@code GET /api/v1/users/me} (#749) with the real {@code SecurityConfig}, the real JWT
 * filter and real MySQL: the caller reads only their own account, never the email or the rest of the profile, and
 * a token that no longer identifies an account is a {@code 401}, exactly like no token at all.
 */
class CurrentUserIntegrationTest extends AbstractAdminUserIntegrationTest {

    private static final String ME_URL = "/api/v1/users/me";
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Autowired private TestRestTemplate http;
    @Autowired private AccessTokenIssuer accessTokenIssuer;

    private User userWithProfile(Role role, String firstName, String lastName) {
        return userRepository.save(User.rehydrate(
            UserId.generate(), Email.of(role.name().toLowerCase() + "-" + System.nanoTime() + "@example.com"),
            passwordEncoder.encode(RAW_PASSWORD), role, UserStatus.ACTIVE, CREATED, CREATED, 0L,
            new UserProfile(firstName, lastName, "+5491112345678", LocalDate.of(1990, 12, 10))
        ));
    }

    private String tokenOf(User user) {
        return accessTokenIssuer.issue(user).token();
    }

    private ResponseEntity<Map> me(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return http.exchange(ME_URL, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private static void assertAuthenticationRequired(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .isTrue();
        assertThat(response.getBody())
            .containsEntry("status", 401)
            .containsEntry("title", "Unauthorized")
            .containsEntry("type", "https://menta.dance/problems/authentication_required")
            .containsEntry("code", "AUTHENTICATION_REQUIRED")
            .containsEntry("detail", "Authentication is required.");
    }

    @Test
    void returns_the_names_and_role_of_a_user_with_profile_and_nothing_else() {
        User student = userWithProfile(Role.STUDENT, "Ada", "Lovelace");

        ResponseEntity<Map> response = me(tokenOf(student));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsOnly(
            Map.entry("userId", student.getId().getValue().toString()),
            Map.entry("firstName", "Ada"),
            Map.entry("lastName", "Lovelace"),
            Map.entry("role", "STUDENT")
        );
    }

    @Test
    void returns_null_names_for_a_user_without_profile() {
        User admin = newUser(Role.ADMIN);

        ResponseEntity<Map> response = me(tokenOf(admin));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
            .hasSize(4)
            .containsEntry("userId", admin.getId().getValue().toString())
            .containsEntry("firstName", null)
            .containsEntry("lastName", null)
            .containsEntry("role", "ADMIN");
    }

    @Test
    void every_token_reads_its_own_account_only() {
        User instructor = userWithProfile(Role.INSTRUCTOR, "Grace", "Hopper");
        User receptionist = userWithProfile(Role.RECEPTIONIST, "Katherine", "Johnson");

        ResponseEntity<Map> asInstructor = me(tokenOf(instructor));
        ResponseEntity<Map> asReceptionist = me(tokenOf(receptionist));

        assertThat(asInstructor.getBody())
            .containsEntry("userId", instructor.getId().getValue().toString())
            .containsEntry("firstName", "Grace")
            .containsEntry("role", "INSTRUCTOR");
        assertThat(asReceptionist.getBody())
            .containsEntry("userId", receptionist.getId().getValue().toString())
            .containsEntry("firstName", "Katherine")
            .containsEntry("role", "RECEPTIONIST");
    }

    @Test
    void rejects_a_request_without_token() {
        assertAuthenticationRequired(me(null));
    }

    @Test
    void rejects_a_garbage_token() {
        assertAuthenticationRequired(me("not-a-jwt"));
    }

    @Test
    void rejects_the_still_valid_token_of_a_deleted_user_like_a_missing_token() {
        User student = userWithProfile(Role.STUDENT, "Ada", "Lovelace");
        String token = tokenOf(student);
        userRepository.deleteById(student.getId());

        assertAuthenticationRequired(me(token));
    }
}
