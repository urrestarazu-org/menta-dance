package com.menta.app.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.menta.app.integration.support.AbstractAuthMySqlIntegrationTest;
import com.menta.auth.application.dto.LoginCommand;
import com.menta.auth.application.dto.LogoutCommand;
import com.menta.auth.application.dto.RefreshCommand;
import com.menta.auth.application.dto.TokenPair;
import com.menta.auth.application.port.in.LoginUseCase;
import com.menta.auth.application.port.in.LogoutUseCase;
import com.menta.auth.application.port.in.RefreshTokenUseCase;
import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.domain.exception.RefreshTokenCompromisedException;
import com.menta.auth.domain.model.RefreshTokenStatus;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.entity.RefreshTokenJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.auth.infrastructure.persistence.repository.RefreshTokenJpaRepository;
import com.menta.shared.domain.vo.Email;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Integration test for atomic auth mutations with outbox.
 *
 * <p>Uses MySQL Testcontainers to prove that:
 * 1. Successful login/refresh/logout commit domain mutation + outbox row together
 * 2. Outbox append failure rolls back the entire domain mutation
 * 3. Token version changes persist through refresh family revocation
 *
 * <p>This test invokes the proxied transactional use case ports, NOT the raw
 * implementation classes. The decorators will be wired in AuthConfiguration
 * as the sole input port beans.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
class TransactionalAuthIntegrationTest extends AbstractAuthMySqlIntegrationTest {

    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private RefreshTokenUseCase refreshTokenUseCase;

    @Autowired
    private LogoutUseCase logoutUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenJpaRepository refreshTokenJpaRepository;

    @Autowired
    private OutboxRowJpaRepository outboxRowJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AuthDegradedGuard authDegradedGuard;

    @MockitoBean
    private TokenBlacklistPort tokenBlacklistPort;

    @MockitoBean
    private org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;

    /**
     * The real limiter would sit on the mocked RedisTemplate above and degrade
     * every login. Failure budgets have their own dedicated coverage; here the
     * budget stays open so these tests remain about transactional atomicity.
     */
    @MockitoBean
    private com.menta.auth.application.port.out.LoginRateLimitPort loginRateLimitPort;

    @MockitoSpyBean
    private OutboxAppender outboxAppender;

    @MockitoSpyBean
    private AccessTokenIssuer accessTokenIssuer;

    private static final String EMAIL = "student@example.com";
    private static final String PASSWORD = "SecurePass123!";
    private static final String CLIENT_FINGERPRINT = "0".repeat(64);
    private User testUser;

    @BeforeEach
    void setUp() {
        // Mock AuthDegradedGuard to allow auth operations
        when(authDegradedGuard.isDegraded()).thenReturn(false);
        when(loginRateLimitPort.check(anyString(), anyString()))
            .thenReturn(com.menta.auth.application.port.out.RateLimitDecision.allowed());

        // Seed test user
        String passwordHash = passwordEncoder.encode(PASSWORD);
        testUser = User.create(Email.of(EMAIL), passwordHash, Role.STUDENT);
        userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        outboxRowJpaRepository.deleteAll();
        refreshTokenJpaRepository.deleteAll();
        userRepository.findByEmail(Email.of(EMAIL))
            .ifPresent(user -> userRepository.deleteById(user.getId()));
    }

    @Test
    void successful_login_commits_the_refresh_token_and_writes_no_outbox_row() {
        LoginCommand command = new LoginCommand(EMAIL, PASSWORD, CLIENT_FINGERPRINT);

        TokenPair tokens = loginUseCase.execute(command);

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();

        // Verify refresh token was persisted
        List<RefreshTokenJpaEntity> refreshTokens = refreshTokenJpaRepository.findAll();
        assertThat(refreshTokens).hasSize(1);
        assertThat(refreshTokens.get(0).getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);

        // #672: auth.AuthUserLoggedIn had no consumer and was removed; login writes no outbox row.
        assertThat(outboxRowJpaRepository.findAll()).isEmpty();
    }

    @Test
    void successful_refresh_commits_the_rotation_and_writes_no_outbox_row() {
        // Setup: login first
        LoginCommand loginCmd = new LoginCommand(EMAIL, PASSWORD, CLIENT_FINGERPRINT);
        TokenPair initial = loginUseCase.execute(loginCmd);

        // Clear outbox to isolate refresh event
        outboxRowJpaRepository.deleteAll();

        // Execute refresh
        RefreshCommand refreshCmd = new RefreshCommand(initial.refreshToken());
        TokenPair rotated = refreshTokenUseCase.execute(refreshCmd);

        assertThat(rotated.accessToken()).isNotBlank();
        assertThat(rotated.refreshToken()).isNotEqualTo(initial.refreshToken());

        // Verify original token is USED
        List<RefreshTokenJpaEntity> allTokens = refreshTokenJpaRepository.findAll();
        assertThat(allTokens).hasSize(2); // original USED + new ACTIVE

        RefreshTokenJpaEntity originalToken = allTokens.stream()
            .filter(t -> t.getStatus() == RefreshTokenStatus.USED)
            .findFirst()
            .orElseThrow();
        assertThat(originalToken).isNotNull();

        // Verify new token is ACTIVE
        RefreshTokenJpaEntity newToken = allTokens.stream()
            .filter(t -> t.getStatus() == RefreshTokenStatus.ACTIVE)
            .findFirst()
            .orElseThrow();
        assertThat(newToken.getFamilyId()).isEqualTo(originalToken.getFamilyId());

        // #672: auth.RefreshRotated had no consumer and was removed; a plain rotation writes no row.
        assertThat(outboxRowJpaRepository.findAll()).isEmpty();
    }

    @Test
    void successful_logout_commits_revocation_and_outbox_atomically() {
        // Setup: login first
        LoginCommand loginCmd = new LoginCommand(EMAIL, PASSWORD, CLIENT_FINGERPRINT);
        TokenPair tokens = loginUseCase.execute(loginCmd);

        // Clear outbox to isolate logout event
        outboxRowJpaRepository.deleteAll();

        // Execute logout
        LogoutCommand logoutCmd = new LogoutCommand(tokens.refreshToken());
        logoutUseCase.execute(logoutCmd);

        // Verify token is REVOKED
        List<RefreshTokenJpaEntity> allTokens = refreshTokenJpaRepository.findAll();
        assertThat(allTokens).hasSize(1);
        assertThat(allTokens.get(0).getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);

        // Verify outbox row for logout
        List<OutboxRowJpaEntity> outboxRows = outboxRowJpaRepository.findAll();
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.get(0).getEventType()).isEqualTo("auth.UserLoggedOut");
    }

    @Test
    void login_failure_after_the_refresh_insert_rolls_back_the_refresh_token() {
        // The last step of the login, after the refresh row is saved. Login no longer writes an
        // outbox row (#672), so atomicity is proven with the step that still follows the insert.
        doThrow(new IllegalStateException("rate limiter unavailable"))
            .when(loginRateLimitPort).resetEmail(anyString());

        assertThatThrownBy(() -> loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD, CLIENT_FINGERPRINT)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("rate limiter unavailable");

        assertThat(refreshTokenJpaRepository.findAll()).isEmpty();
        assertThat(outboxRowJpaRepository.findAll()).isEmpty();
    }

    @Test
    void refresh_failure_after_the_rotation_rolls_back_the_rotation() {
        TokenPair initial = loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD, CLIENT_FINGERPRINT));
        outboxRowJpaRepository.deleteAll();
        // Issuing the new access token is the step after both refresh rows are saved. A plain
        // rotation writes no outbox row any more (#672), so atomicity is proven with that step.
        doThrow(new IllegalStateException("token issuer unavailable"))
            .when(accessTokenIssuer).issue(any(User.class));

        assertThatThrownBy(() -> refreshTokenUseCase.execute(new RefreshCommand(initial.refreshToken())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("token issuer unavailable");

        List<RefreshTokenJpaEntity> tokens = refreshTokenJpaRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
        assertThat(outboxRowJpaRepository.findAll()).isEmpty();
    }

    @Test
    void logout_outbox_failure_rolls_back_revocation() {
        TokenPair initial = loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD, CLIENT_FINGERPRINT));
        outboxRowJpaRepository.deleteAll();
        doThrow(new IllegalStateException("outbox unavailable"))
            .when(outboxAppender).append(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> logoutUseCase.execute(new LogoutCommand(initial.refreshToken())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("outbox unavailable");

        List<RefreshTokenJpaEntity> tokens = refreshTokenJpaRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
        assertThat(outboxRowJpaRepository.findAll()).isEmpty();
    }

    @Test
    void refresh_family_revocation_persists_bumped_token_version() {
        // Setup: login and rotate once to create a family
        LoginCommand loginCmd = new LoginCommand(EMAIL, PASSWORD, CLIENT_FINGERPRINT);
        TokenPair initial = loginUseCase.execute(loginCmd);

        RefreshCommand refreshCmd = new RefreshCommand(initial.refreshToken());
        TokenPair rotated = refreshTokenUseCase.execute(refreshCmd);

        // Get original token version
        User userBefore = userRepository.findByEmail(Email.of(EMAIL)).orElseThrow();
        long versionBefore = userBefore.getTokenVersion();
        assertThat(versionBefore).isEqualTo(1L);

        // Present the USED token (original) again → triggers family revoke + version bump
        assertThatThrownBy(() -> refreshTokenUseCase.execute(new RefreshCommand(initial.refreshToken())))
            .isInstanceOf(RefreshTokenCompromisedException.class);

        // Clear JPA cache to force fresh DB read (transaction already committed)
        entityManager.clear();

        // Verify token version was bumped AND persisted (read directly from DB)
        Long tokenVersionFromDb = jdbcTemplate.queryForObject(
            "SELECT token_version FROM auth_users WHERE email = ?",
            Long.class,
            EMAIL
        );
        assertThat(tokenVersionFromDb).isEqualTo(2L);

        // Also verify through repository
        User userAfter = userRepository.findByEmail(Email.of(EMAIL)).orElseThrow();
        assertThat(userAfter.getTokenVersion()).isEqualTo(2L);

        // Verify all tokens in family are REVOKED
        List<RefreshTokenJpaEntity> allTokens = refreshTokenJpaRepository.findAll();
        assertThat(allTokens).allMatch(t -> t.getStatus() == RefreshTokenStatus.REVOKED);
    }

    @Test
    void logout_with_compromised_token_persists_version_bump() {
        // Setup: login and rotate once
        LoginCommand loginCmd = new LoginCommand(EMAIL, PASSWORD, CLIENT_FINGERPRINT);
        TokenPair initial = loginUseCase.execute(loginCmd);

        RefreshCommand refreshCmd = new RefreshCommand(initial.refreshToken());
        refreshTokenUseCase.execute(refreshCmd);

        // Present USED token to logout → triggers family revoke + version bump
        assertThatThrownBy(() -> logoutUseCase.execute(new LogoutCommand(initial.refreshToken())))
            .isInstanceOf(RefreshTokenCompromisedException.class);

        // Clear JPA cache to force fresh DB read (transaction already committed)
        entityManager.clear();

        // Verify token version was bumped to 2 (read directly from DB)
        Long tokenVersionFromDb = jdbcTemplate.queryForObject(
            "SELECT token_version FROM auth_users WHERE email = ?",
            Long.class,
            EMAIL
        );
        assertThat(tokenVersionFromDb).isEqualTo(2L);

        // Also verify through repository
        User userAfter = userRepository.findByEmail(Email.of(EMAIL)).orElseThrow();
        assertThat(userAfter.getTokenVersion()).isEqualTo(2L);

        // Verify family revocation outbox event exists
        List<OutboxRowJpaEntity> outboxRows = outboxRowJpaRepository.findAll();

        // Only the revocation: login and a plain rotation write no outbox row (#672)
        assertThat(outboxRows).hasSize(1);

        // Verify RefreshRevoked event was persisted
        boolean hasRevokeEvent = outboxRows.stream()
            .anyMatch(row -> row.getEventType().equals("auth.RefreshRevoked"));

        assertThat(hasRevokeEvent)
            .as("RefreshRevoked event should be present")
            .isTrue();
    }
}
