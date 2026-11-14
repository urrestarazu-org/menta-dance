package com.menta.app.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.menta.app.integration.support.AbstractAuthMySqlIntegrationTest;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.infrastructure.demo.DemoUserFixture;
import com.menta.physical.application.port.out.CheckInRateLimitPort;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Proves, with the real demo seed, real MySQL and the real login, that the demo instructor has the name
 * "Profesor Demo" (#749): right after startup, and also when an instructor seeded before #749 (no profile) is
 * found on the next run of the seed, while a profile someone already has is never overwritten.
 *
 * <p>The seed runs as an {@code ApplicationRunner} at startup; "the next startup" is simulated by running that same
 * runner again against the rows this test rewrites, which is exactly what a restart executes.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"integration-test", "demo"})
class DemoInstructorProfileIntegrationTest extends AbstractAuthMySqlIntegrationTest {

    private static final String INSTRUCTOR_EMAIL = "profesor@menta.dance";
    private static final String ADMIN_EMAIL = "admin@menta.dance";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DemoUserFixture demoUserFixture;

    @Value("${demo.users.instructor-password}") private String instructorPassword;
    @Value("${demo.users.admin-password}") private String adminPassword;

    @MockitoBean private AuthDegradedGuard authDegradedGuard;
    @MockitoBean private TokenBlacklistPort tokenBlacklistPort;
    @MockitoBean private LoginRateLimitPort loginRateLimitPort;
    @MockitoBean private ActivationRateLimitPort activationRateLimitPort;
    @MockitoBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockitoBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockitoBean private com.menta.billing.application.port.out.BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockitoBean private com.menta.billing.application.port.out.BankTransferRateLimitPort bankTransferRateLimitPort;
    @MockitoBean
    private com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;
    @MockitoBean private CheckInRateLimitPort checkInRateLimitPort;

    /**
     * The instructor's names exactly as the startup seed left them, read before any test rewrites them. Static
     * because JUnit builds one instance per test while the Spring context, and so the seeded row, is shared.
     */
    private static Map<String, Object> namesAfterStartup;

    @BeforeEach
    void wireFakesAndSnapshotTheSeededNames() {
        when(authDegradedGuard.isDegraded()).thenReturn(false);
        when(loginRateLimitPort.check(anyString(), anyString())).thenReturn(RateLimitDecision.allowed());
        lenient().when(tokenBlacklistPort.currentTokenVersion(anyString())).thenReturn(OptionalLong.empty());
        if (namesAfterStartup == null) {
            namesAfterStartup = jdbcTemplate.queryForMap(
                "SELECT first_name, last_name FROM auth_users WHERE email = ?", INSTRUCTOR_EMAIL);
        }
    }

    /** Puts back what startup seeded, so every test starts from the state right after startup. */
    @AfterEach
    void restoreTheSeededNames() {
        setInstructorNames(
            (String) namesAfterStartup.get("first_name"), (String) namesAfterStartup.get("last_name"));
    }

    private void setInstructorNames(String firstName, String lastName) {
        jdbcTemplate.update("UPDATE auth_users SET first_name = ?, last_name = ? WHERE email = ?",
            firstName, lastName, INSTRUCTOR_EMAIL);
    }

    private String login(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response = http.exchange("/api/v1/auth/login", HttpMethod.POST,
            new HttpEntity<>(Map.of("email", email, "password", password), headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("access_token");
    }

    private Map me(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<Map> response =
            http.exchange("/api/v1/users/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @Test
    void after_startup_the_demo_instructor_is_profesor_demo() {
        Map me = me(login(INSTRUCTOR_EMAIL, instructorPassword));

        assertThat(me)
            .containsEntry("firstName", "Profesor")
            .containsEntry("lastName", "Demo")
            .containsEntry("role", "INSTRUCTOR");
    }

    @Test
    void after_startup_the_other_demo_users_still_have_no_name() {
        Map me = me(login(ADMIN_EMAIL, adminPassword));

        assertThat(me)
            .containsEntry("firstName", null)
            .containsEntry("lastName", null)
            .containsEntry("role", "ADMIN");
    }

    @Test
    void the_next_startup_gives_a_profile_less_instructor_the_demo_profile_and_keeps_its_password()
        throws Exception {
        setInstructorNames(null, null);

        demoUserFixture.run(new DefaultApplicationArguments());

        Map me = me(login(INSTRUCTOR_EMAIL, instructorPassword));
        assertThat(me)
            .containsEntry("firstName", "Profesor")
            .containsEntry("lastName", "Demo")
            .containsEntry("role", "INSTRUCTOR");
    }

    @Test
    void the_next_startup_never_overwrites_a_profile_the_instructor_already_has() throws Exception {
        setInstructorNames("Marta", "Gomez");

        demoUserFixture.run(new DefaultApplicationArguments());

        Map me = me(login(INSTRUCTOR_EMAIL, instructorPassword));
        assertThat(me)
            .containsEntry("firstName", "Marta")
            .containsEntry("lastName", "Gomez");
    }
}
