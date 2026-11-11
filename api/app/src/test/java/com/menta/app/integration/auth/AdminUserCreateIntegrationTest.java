package com.menta.app.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.application.port.out.ActivationDeliveryCipher;
import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.infrastructure.persistence.entity.ActivationTokenJpaEntity;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.entity.UserAuditJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.ActivationTokenJpaRepository;
import com.menta.auth.infrastructure.persistence.repository.UserAuditJpaRepository;
import com.menta.shared.domain.vo.Email;
import java.util.Comparator;
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
 * End-to-end coverage of {@code POST /api/v1/admin/users} (#378) against real MySQL: the account is created
 * pending with an unusable password, cannot log in, is activated by its holder choosing a password through the
 * public activation endpoints, and the creation is audited and queues exactly one activation email.
 */
class AdminUserCreateIntegrationTest extends AbstractAdminUserIntegrationTest {

    private static final String USERS_URL = "/api/v1/admin/users";
    private static final String STAFF_EMAIL = "recepcion.nueva@example.com";
    private static final String CHOSEN_PASSWORD = "Chosen1Password";

    @Autowired private TestRestTemplate http;
    @Autowired private UserAuditJpaRepository auditRepository;
    @Autowired private ActivationTokenJpaRepository activationTokenRepository;
    @Autowired private ActivationDeliveryCipher deliveryCipher;

    private String adminAccessToken() {
        User admin = newUser(Role.ADMIN);
        return accessTokenOf(admin.getEmail().getValue(), RAW_PASSWORD);
    }

    private String accessTokenOf(String email, String password) {
        ResponseEntity<Map> response = login(email, password);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("access_token");
    }

    private ResponseEntity<Map> login(String email, String password) {
        return http.exchange("/api/v1/auth/login", HttpMethod.POST,
            new HttpEntity<>(Map.of("email", email, "password", password), json()), Map.class);
    }

    private static HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<Map> createUser(String accessToken, Map<String, Object> body) {
        HttpHeaders headers = json();
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        return http.exchange(USERS_URL, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private static Map<String, Object> staffBody(String email, Role role) {
        return Map.of("email", email, "firstName", "Ada", "lastName", "Lovelace", "role", role.name());
    }

    private ResponseEntity<Map> post(String url, Map<String, Object> body) {
        return http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, json()), Map.class);
    }

    private User stored(String email) {
        return userRepository.findByEmail(Email.of(email)).orElseThrow();
    }

    /** The raw token of the newest live activation token: what the email would carry. */
    private String liveRawToken(UserId userId) {
        ActivationTokenJpaEntity token = activationTokenRepository.findAll().stream()
            .filter(candidate -> candidate.getUserId().equals(userId.getValue()))
            .filter(candidate -> candidate.getUsedAt() == null && candidate.getInvalidatedAt() == null)
            .max(Comparator.comparing(ActivationTokenJpaEntity::getCreatedAt))
            .orElseThrow();
        String plaintext = deliveryCipher.decrypt(DeliveryEnvelope.of(
            token.getDeliveryCiphertext(), token.getDeliveryNonce(), token.getDeliveryKeyVersion()
        ));
        return plaintext.substring(plaintext.indexOf('|') + 1);
    }

    private List<OutboxRowJpaEntity> activationEvents() {
        return outboxRowJpaRepository.findAll().stream()
            .filter(row -> "auth.AccountActivationRequested".equals(row.getEventType())).toList();
    }

    @Test
    void creates_a_pending_account_with_an_unusable_password_audits_it_and_queues_one_activation_email() {
        final User admin = newUser(Role.ADMIN);
        final String adminToken = accessTokenOf(admin.getEmail().getValue(), RAW_PASSWORD);

        ResponseEntity<Map> response = createUser(adminToken, staffBody(STAFF_EMAIL, Role.RECEPTIONIST));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = response.getBody();
        assertThat(body.get("email")).isEqualTo(STAFF_EMAIL);
        assertThat(body.get("firstName")).isEqualTo("Ada");
        assertThat(body.get("role")).isEqualTo("RECEPTIONIST");
        assertThat(body.get("status")).isEqualTo("PENDING_ACTIVATION");
        assertThat(body.get("demo")).isEqualTo(false);
        assertThat(body.keySet()).doesNotContain("password", "passwordHash", "tokenVersion");

        User created = stored(STAFF_EMAIL);
        assertThat(created.getId().getValue().toString()).isEqualTo(body.get("id"));
        assertThat(created.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(created.isMustSetPassword()).isTrue();
        assertThat(created.isDemo()).isFalse();
        assertThat(created.getPasswordHash()).startsWith("$2");

        List<UserAuditJpaEntity> audit = auditRepository.findByUserIdOrderByCreatedAtAsc(created.getId().getValue());
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).getAction()).isEqualTo("USER_CREATED");
        assertThat(audit.get(0).getActorId()).isEqualTo(admin.getId().getValue());
        assertThat(audit.get(0).getPreviousValue()).isNull();
        assertThat(audit.get(0).getNewValue()).isEqualTo("RECEPTIONIST");

        String rawToken = liveRawToken(created.getId());
        List<OutboxRowJpaEntity> events = activationEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getPayload()).doesNotContain(rawToken).doesNotContain(CHOSEN_PASSWORD);
    }

    @Test
    void every_new_account_gets_its_own_unusable_hash() {
        String adminToken = adminAccessToken();

        createUser(adminToken, staffBody("uno@example.com", Role.INSTRUCTOR));
        createUser(adminToken, staffBody("dos@example.com", Role.INSTRUCTOR));

        assertThat(stored("uno@example.com").getPasswordHash())
            .isNotEqualTo(stored("dos@example.com").getPasswordHash());
    }

    @Test
    void the_new_account_cannot_log_in_before_activating_with_any_password() {
        String adminToken = adminAccessToken();
        createUser(adminToken, staffBody(STAFF_EMAIL, Role.ADMIN));

        for (String guess : List.of(RAW_PASSWORD, CHOSEN_PASSWORD, "password", "Password1", "x")) {
            assertThat(login(STAFF_EMAIL, guess).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(stored(STAFF_EMAIL).getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
    }

    @Test
    void the_holder_activates_choosing_their_own_password_and_then_logs_in() {
        createUser(adminAccessToken(), staffBody(STAFF_EMAIL, Role.RECEPTIONIST));
        UserId staffId = stored(STAFF_EMAIL).getId();
        String rawToken = liveRawToken(staffId);

        ResponseEntity<Map> validation = http.getForEntity("/api/v1/auth/activate/" + rawToken, Map.class);
        assertThat(validation.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(validation.getBody()).containsEntry("passwordRequired", true);

        ResponseEntity<Map> missing = post("/api/v1/auth/activate", Map.of("token", rawToken));
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missing.getBody()).containsEntry("code", "PASSWORD_REQUIRED");

        ResponseEntity<Map> weak = post("/api/v1/auth/activate", Map.of("token", rawToken, "password", "weak"));
        assertThat(weak.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(weak.getBody()).containsEntry("code", "WEAK_PASSWORD");
        assertThat(stored(STAFF_EMAIL).getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);

        // The failed attempts did not burn the link.
        ResponseEntity<Void> activated = http.exchange("/api/v1/auth/activate", HttpMethod.POST,
            new HttpEntity<>(Map.of("token", rawToken, "password", CHOSEN_PASSWORD), json()), Void.class);
        assertThat(activated.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        User active = stored(STAFF_EMAIL);
        assertThat(active.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(active.isMustSetPassword()).isFalse();
        assertThat(login(STAFF_EMAIL, CHOSEN_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(STAFF_EMAIL, RAW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> reused = post("/api/v1/auth/activate",
            Map.of("token", rawToken, "password", CHOSEN_PASSWORD));
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reused.getBody()).containsEntry("code", "ACTIVATION_TOKEN_INVALID");
    }

    @Test
    void resending_the_activation_works_for_an_account_created_by_an_administrator() {
        createUser(adminAccessToken(), staffBody(STAFF_EMAIL, Role.INSTRUCTOR));
        UserId staffId = stored(STAFF_EMAIL).getId();
        final String firstToken = liveRawToken(staffId);

        ResponseEntity<Void> resent = http.exchange("/api/v1/auth/resend-activation", HttpMethod.POST,
            new HttpEntity<>(Map.of("email", STAFF_EMAIL), json()), Void.class);

        assertThat(resent.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String secondToken = liveRawToken(staffId);
        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThat(activationEvents()).hasSize(2);
        assertThat(post("/api/v1/auth/activate", Map.of("token", firstToken, "password", CHOSEN_PASSWORD))
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<Void> activated = http.exchange("/api/v1/auth/activate", HttpMethod.POST,
            new HttpEntity<>(Map.of("token", secondToken, "password", CHOSEN_PASSWORD), json()), Void.class);
        assertThat(activated.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void a_duplicated_email_is_a_409_that_creates_nothing_else() {
        final User existing = newUser(Role.STUDENT);
        final String adminToken = adminAccessToken();
        final long auditBefore = auditRepository.count();

        ResponseEntity<Map> response = createUser(
            adminToken, staffBody(existing.getEmail().getValue().toUpperCase(), Role.INSTRUCTOR)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "EMAIL_ALREADY_REGISTERED");
        assertThat(auditRepository.count()).isEqualTo(auditBefore);
        assertThat(activationEvents()).isEmpty();
        assertThat(activationTokenRepository.count()).isZero();
    }

    @Test
    void only_an_admin_can_create_accounts_and_students_are_not_creatable() {
        final String studentToken = accessTokenOf(newUser(Role.STUDENT).getEmail().getValue(), RAW_PASSWORD);
        final String receptionistToken = accessTokenOf(newUser(Role.RECEPTIONIST).getEmail().getValue(), RAW_PASSWORD);

        assertThat(createUser(null, staffBody(STAFF_EMAIL, Role.ADMIN)).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(createUser(studentToken, staffBody(STAFF_EMAIL, Role.ADMIN)).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(createUser(receptionistToken, staffBody(STAFF_EMAIL, Role.ADMIN)).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> student = createUser(adminAccessToken(), staffBody(STAFF_EMAIL, Role.STUDENT));
        assertThat(student.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(student.getBody()).containsEntry("code", "INVALID_REQUEST");
        assertThat(userRepository.findByEmail(Email.of(STAFF_EMAIL))).isEmpty();
    }
}
