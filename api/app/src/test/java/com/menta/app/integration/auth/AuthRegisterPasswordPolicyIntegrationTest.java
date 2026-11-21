package com.menta.app.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.infrastructure.persistence.repository.ActivationTokenJpaRepository;
import com.menta.shared.domain.vo.Email;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The public registration applies the domain password policy (#718), against real MySQL.
 *
 * <p>Before the fix a weak password answered {@code 202} and the account was created: only the BFF form checked the
 * policy. The policy is now checked before the duplicate lookup and before the rate-limit budget is spent, so the
 * {@code 400} does not depend on whether the email is already registered and the anti-enumeration {@code 202} of a
 * valid registration is untouched.</p>
 */
class AuthRegisterPasswordPolicyIntegrationTest extends AbstractAdminUserIntegrationTest {

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String ALIAS_URL = "/api/v1/users/register";
    private static final String EMAIL = "weak.password@example.com";
    private static final String WEAK_PASSWORD = "weakpass";
    private static final String WEAK_DETAIL =
        "La contraseña no cumple los requisitos: al menos una mayúscula, al menos un número.";

    @Autowired private TestRestTemplate http;
    @Autowired private ActivationRateLimitPort activationRateLimit;
    @Autowired private ActivationTokenJpaRepository activationTokenRepository;

    private ResponseEntity<Map> register(String url, String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
            "email", email, "password", password, "role", "STUDENT",
            "firstName", "Ada", "lastName", "Lovelace", "phone", "+5491100000000"
        );
        return http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private void assertWeakPasswordProblem(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .isTrue();
        assertThat(response.getBody())
            .containsEntry("code", "WEAK_PASSWORD")
            .containsEntry("detail", WEAK_DETAIL);
        assertThat(response.getBody().toString()).doesNotContain(WEAK_PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {REGISTER_URL, ALIAS_URL})
    void a_weak_password_is_rejected_with_400_and_creates_nothing(String url) {
        ResponseEntity<Map> response = register(url, EMAIL, WEAK_PASSWORD);

        assertWeakPasswordProblem(response);
        assertThat(userRepository.findByEmail(Email.of(EMAIL))).isEmpty();
        assertThat(activationTokenRepository.findAll()).isEmpty();
        assertThat(outboxRowJpaRepository.findAll()).isEmpty();
        verify(activationRateLimit, never()).consume(anyString(), any());
    }

    @Test
    void a_weak_password_answers_the_same_400_whether_or_not_the_email_is_already_registered() {
        ResponseEntity<Map> fresh = register(REGISTER_URL, EMAIL, WEAK_PASSWORD);
        assertWeakPasswordProblem(fresh);
        assertThat(userRepository.findByEmail(Email.of(EMAIL))).isEmpty();

        userRepository.save(User.create(
            Email.of(EMAIL), passwordEncoder.encode(RAW_PASSWORD), Role.STUDENT, java.time.Instant.now()
        ));
        ResponseEntity<Map> duplicate = register(REGISTER_URL, EMAIL, WEAK_PASSWORD);

        assertWeakPasswordProblem(duplicate);
        assertThat(duplicate.getBody()).isEqualTo(fresh.getBody());
    }

    @Test
    void a_valid_password_still_answers_202_for_a_new_and_for_an_already_registered_email() {
        ResponseEntity<Map> created = register(REGISTER_URL, EMAIL, RAW_PASSWORD);
        ResponseEntity<Map> duplicate = register(REGISTER_URL, EMAIL, RAW_PASSWORD);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(userRepository.findByEmail(Email.of(EMAIL)).orElseThrow().getStatus())
            .isEqualTo(UserStatus.PENDING_ACTIVATION);
    }
}
